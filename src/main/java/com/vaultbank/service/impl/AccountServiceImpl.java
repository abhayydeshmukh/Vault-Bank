package com.vaultbank.service.impl;

import com.vaultbank.dto.event.AccountCreatedEvent;
import com.vaultbank.dto.request.CreateAccountRequest;
import com.vaultbank.dto.response.AccountResponse;
import com.vaultbank.entity.Account;
import com.vaultbank.entity.AccountStatus;
import com.vaultbank.entity.Role;
import com.vaultbank.entity.User;
import com.vaultbank.exception.InvalidAccountException;
import com.vaultbank.exception.ResourceNotFoundException;
import com.vaultbank.repository.AccountRepository;
import com.vaultbank.repository.UserRepository;
import com.vaultbank.service.AccountService;
import com.vaultbank.service.KafkaProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final KafkaProducerService kafkaProducerService;

    public AccountServiceImpl(AccountRepository accountRepository,
                              UserRepository userRepository,
                              KafkaProducerService kafkaProducerService) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Override
    @Transactional
    public AccountResponse createAccount(String email, CreateAccountRequest request) {
        log.info("Creating a new {} account for user: {}", request.getAccountType(), email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        String accountNumber = generateUniqueAccountNumber();

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .accountType(request.getAccountType())
                .balance(BigDecimal.ZERO) // Start with 0.00 balance
                .status(AccountStatus.ACTIVE)
                .user(user)
                .build();

        Account savedAccount = accountRepository.save(account);
        log.info("Successfully created account: {}", savedAccount.getAccountNumber());

        // Publish event to Kafka
        AccountCreatedEvent event = AccountCreatedEvent.builder()
                .accountNumber(savedAccount.getAccountNumber())
                .accountType(savedAccount.getAccountType().name())
                .ownerEmail(savedAccount.getUser().getEmail())
                .createdAt(java.time.LocalDateTime.now())
                .build();
        kafkaProducerService.sendAccountCreatedEvent(event);

        return mapToAccountResponse(savedAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(String email, String accountNumber) {
        log.info("Fetching account: {}", accountNumber);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        // Enforce owner or admin authorization check
        if (!account.getUser().getId().equals(user.getId()) && !user.getRole().equals(Role.ROLE_ADMIN)) {
            throw new AccessDeniedException("You do not have permission to view this account.");
        }

        return mapToAccountResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(String email) {
        log.info("Fetching accounts for user: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        List<Account> accounts = accountRepository.findByUser(user);
        return accounts.stream()
                .map(this::mapToAccountResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void closeAccount(String email, String accountNumber) {
        log.info("Closing account: {}", accountNumber);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You are not the owner of this account.");
        }

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidAccountException("Account is already closed.");
        }

        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new InvalidAccountException("Cannot close account with positive balance. Please withdraw all funds first.");
        }

        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);
        log.info("Account successfully closed: {}", accountNumber);
    }

    @Override
    @Transactional
    public AccountResponse freezeAccount(String accountNumber) {
        log.info("Freezing account: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidAccountException("Cannot freeze a closed account.");
        }

        account.setStatus(AccountStatus.FROZEN);
        Account saved = accountRepository.save(account);
        log.info("Account successfully frozen: {}", accountNumber);
        return mapToAccountResponse(saved);
    }

    @Override
    @Transactional
    public AccountResponse unfreezeAccount(String accountNumber) {
        log.info("Unfreezing account: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidAccountException("Cannot unfreeze a closed account.");
        }

        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);
        log.info("Account successfully unfrozen: {}", accountNumber);
        return mapToAccountResponse(saved);
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            // Generate random 12 digit number
            long number = ThreadLocalRandom.current().nextLong(100000000000L, 1000000000000L);
            accountNumber = String.valueOf(number);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private AccountResponse mapToAccountResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType().name())
                .balance(account.getBalance())
                .status(account.getStatus().name())
                .ownerEmail(account.getUser().getEmail())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
