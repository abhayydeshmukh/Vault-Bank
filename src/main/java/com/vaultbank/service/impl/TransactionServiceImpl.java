package com.vaultbank.service.impl;

import com.vaultbank.dto.event.DepositEvent;
import com.vaultbank.dto.event.TransferEvent;
import com.vaultbank.dto.event.WithdrawalEvent;
import com.vaultbank.dto.request.DepositRequest;
import com.vaultbank.dto.request.TransferRequest;
import com.vaultbank.dto.request.WithdrawRequest;
import com.vaultbank.dto.response.TransactionResponse;
import com.vaultbank.entity.*;
import com.vaultbank.exception.*;
import com.vaultbank.repository.AccountRepository;
import com.vaultbank.repository.TransactionRepository;
import com.vaultbank.repository.UserRepository;
import com.vaultbank.service.KafkaProducerService;
import com.vaultbank.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final KafkaProducerService kafkaProducerService;

    public TransactionServiceImpl(TransactionRepository transactionRepository,
                                  AccountRepository accountRepository,
                                  UserRepository userRepository,
                                  KafkaProducerService kafkaProducerService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Override
    @Transactional
    public TransactionResponse deposit(DepositRequest request) {
        log.info("Processing deposit of {} to account: {}", request.getAmount(), request.getAccountNumber());

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegativeAmountException("Deposit amount must be greater than zero.");
        }

        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.getAccountNumber()));

        validateAccountStatus(account);

        // Update Balance
        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        // Create transaction entry
        String reference = generateTransactionReference();
        Transaction transaction = Transaction.builder()
                .transactionReference(reference)
                .transactionType(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .description(request.getDescription())
                .destinationAccount(account)
                .build();

        Transaction savedTx = transactionRepository.save(transaction);
        log.info("Deposit successful. Reference: {}", reference);

        // Publish event to Kafka
        DepositEvent event = DepositEvent.builder()
                .accountNumber(savedTx.getDestinationAccount().getAccountNumber())
                .amount(savedTx.getAmount())
                .transactionReference(savedTx.getTransactionReference())
                .timestamp(savedTx.getCreatedAt() != null ? savedTx.getCreatedAt() : LocalDateTime.now())
                .build();
        kafkaProducerService.sendDepositEvent(event);

        return mapToTransactionResponse(savedTx);
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(String email, WithdrawRequest request) {
        log.info("Processing withdrawal of {} from account: {}", request.getAmount(), request.getAccountNumber());

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegativeAmountException("Withdrawal amount must be greater than zero.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.getAccountNumber()));

        // Enforce owner check
        if (!account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not own this account.");
        }

        validateAccountStatus(account);

        // Balance check
        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance to complete withdrawal.");
        }

        // Update Balance
        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        // Create transaction entry
        String reference = generateTransactionReference();
        Transaction transaction = Transaction.builder()
                .transactionReference(reference)
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .description(request.getDescription())
                .sourceAccount(account)
                .build();

        Transaction savedTx = transactionRepository.save(transaction);
        log.info("Withdrawal successful. Reference: {}", reference);

        // Publish event to Kafka
        WithdrawalEvent event = WithdrawalEvent.builder()
                .accountNumber(savedTx.getSourceAccount().getAccountNumber())
                .amount(savedTx.getAmount())
                .transactionReference(savedTx.getTransactionReference())
                .timestamp(savedTx.getCreatedAt() != null ? savedTx.getCreatedAt() : LocalDateTime.now())
                .build();
        kafkaProducerService.sendWithdrawalEvent(event);

        return mapToTransactionResponse(savedTx);
    }

    @Override
    @Transactional
    public TransactionResponse transfer(String email, TransferRequest request) {
        log.info("Processing transfer of {} from {} to {}", 
                request.getAmount(), request.getSourceAccountNumber(), request.getDestinationAccountNumber());

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegativeAmountException("Transfer amount must be greater than zero.");
        }

        if (request.getSourceAccountNumber().equals(request.getDestinationAccountNumber())) {
            throw new InvalidTransferException("Source and destination accounts cannot be the same.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        Account sourceAccount = accountRepository.findByAccountNumber(request.getSourceAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + request.getSourceAccountNumber()));

        Account destinationAccount = accountRepository.findByAccountNumber(request.getDestinationAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + request.getDestinationAccountNumber()));

        // Check ownership of source account
        if (!sourceAccount.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not own the source account.");
        }

        validateAccountStatus(sourceAccount);
        validateAccountStatus(destinationAccount);

        // Balance Check
        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance to complete transfer.");
        }

        // Apply mutations
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
        destinationAccount.setBalance(destinationAccount.getBalance().add(request.getAmount()));

        // Save accounts - JPA Hibernate version dirty check handles optimistic locking here
        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        // Create ledger entry
        String reference = generateTransactionReference();
        Transaction transaction = Transaction.builder()
                .transactionReference(reference)
                .transactionType(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .description(request.getDescription())
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .build();

        Transaction savedTx = transactionRepository.save(transaction);
        log.info("Transfer successful. Reference: {}", reference);

        // Publish event to Kafka
        TransferEvent event = TransferEvent.builder()
                .sourceAccountNumber(savedTx.getSourceAccount().getAccountNumber())
                .destinationAccountNumber(savedTx.getDestinationAccount().getAccountNumber())
                .amount(savedTx.getAmount())
                .transactionReference(savedTx.getTransactionReference())
                .timestamp(savedTx.getCreatedAt() != null ? savedTx.getCreatedAt() : LocalDateTime.now())
                .build();
        kafkaProducerService.sendTransferEvent(event);

        return mapToTransactionResponse(savedTx);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistory(String email, String accountNumber) {
        log.info("Retrieving transaction history for account: {}", accountNumber);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        // Ownership or admin check
        if (!account.getUser().getId().equals(user.getId()) && !user.getRole().equals(Role.ROLE_ADMIN)) {
            throw new AccessDeniedException("You do not have permission to view transaction history for this account.");
        }

        List<Transaction> transactions = transactionRepository.findTransactionHistory(account);
        return transactions.stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());
    }

    private void validateAccountStatus(Account account) {
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidAccountException("Account " + account.getAccountNumber() + " is CLOSED. Transaction rejected.");
        }
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException("Account " + account.getAccountNumber() + " is FROZEN. Transaction rejected.");
        }
    }

    private String generateTransactionReference() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 16);
    }

    private TransactionResponse mapToTransactionResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .transactionReference(tx.getTransactionReference())
                .transactionType(tx.getTransactionType().name())
                .amount(tx.getAmount())
                .description(tx.getDescription())
                .sourceAccountNumber(tx.getSourceAccount() != null ? tx.getSourceAccount().getAccountNumber() : null)
                .destinationAccountNumber(tx.getDestinationAccount() != null ? tx.getDestinationAccount().getAccountNumber() : null)
                .createdAt(tx.getCreatedAt() != null ? tx.getCreatedAt() : LocalDateTime.now())
                .build();
    }
}
