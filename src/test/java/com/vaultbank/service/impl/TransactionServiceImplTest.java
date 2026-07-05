package com.vaultbank.service.impl;

import com.vaultbank.dto.event.DepositEvent;
import com.vaultbank.dto.event.TransferEvent;
import com.vaultbank.dto.event.WithdrawalEvent;
import com.vaultbank.dto.request.DepositRequest;
import com.vaultbank.dto.request.TransferRequest;
import com.vaultbank.dto.request.WithdrawRequest;
import com.vaultbank.dto.response.TransactionResponse;
import com.vaultbank.entity.Account;
import com.vaultbank.entity.AccountStatus;
import com.vaultbank.entity.Transaction;
import com.vaultbank.entity.TransactionType;
import com.vaultbank.entity.User;
import com.vaultbank.exception.AccountFrozenException;
import com.vaultbank.exception.InsufficientBalanceException;
import com.vaultbank.exception.NegativeAmountException;
import com.vaultbank.repository.AccountRepository;
import com.vaultbank.repository.TransactionRepository;
import com.vaultbank.repository.UserRepository;
import com.vaultbank.service.KafkaProducerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @Test
    void deposit_Success() {
        DepositRequest request = DepositRequest.builder()
                .accountNumber("123456789012")
                .amount(new BigDecimal("100.00"))
                .description("Deposit test")
                .build();

        Account account = Account.builder()
                .accountNumber("123456789012")
                .balance(new BigDecimal("50.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        Transaction tx = Transaction.builder()
                .transactionReference("TXN-123")
                .transactionType(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .destinationAccount(account)
                .build();

        when(accountRepository.findByAccountNumber(request.getAccountNumber())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(tx);

        TransactionResponse response = transactionService.deposit(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("150.00"), account.getBalance());
        assertEquals("TXN-123", response.getTransactionReference());
        verify(kafkaProducerService, times(1)).sendDepositEvent(any(DepositEvent.class));
    }

    @Test
    void deposit_NegativeAmount_ThrowsException() {
        DepositRequest request = DepositRequest.builder()
                .accountNumber("123456789012")
                .amount(new BigDecimal("-10.00"))
                .build();

        assertThrows(NegativeAmountException.class, () -> transactionService.deposit(request));
    }

    @Test
    void withdraw_Success() {
        WithdrawRequest request = WithdrawRequest.builder()
                .accountNumber("123456789012")
                .amount(new BigDecimal("50.00"))
                .build();

        User user = User.builder().id(1L).email("owner@vaultbank.com").build();
        Account account = Account.builder()
                .accountNumber("123456789012")
                .balance(new BigDecimal("100.00"))
                .status(AccountStatus.ACTIVE)
                .user(user)
                .build();

        Transaction tx = Transaction.builder()
                .transactionReference("TXN-456")
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .sourceAccount(account)
                .build();

        when(userRepository.findByEmail("owner@vaultbank.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByAccountNumber(request.getAccountNumber())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(tx);

        TransactionResponse response = transactionService.withdraw("owner@vaultbank.com", request);

        assertNotNull(response);
        assertEquals(new BigDecimal("50.00"), account.getBalance());
        verify(kafkaProducerService, times(1)).sendWithdrawalEvent(any(WithdrawalEvent.class));
    }

    @Test
    void withdraw_InsufficientBalance_ThrowsException() {
        WithdrawRequest request = WithdrawRequest.builder()
                .accountNumber("123456789012")
                .amount(new BigDecimal("150.00"))
                .build();

        User user = User.builder().id(1L).email("owner@vaultbank.com").build();
        Account account = Account.builder()
                .accountNumber("123456789012")
                .balance(new BigDecimal("100.00"))
                .status(AccountStatus.ACTIVE)
                .user(user)
                .build();

        when(userRepository.findByEmail("owner@vaultbank.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByAccountNumber(request.getAccountNumber())).thenReturn(Optional.of(account));

        assertThrows(InsufficientBalanceException.class, () -> transactionService.withdraw("owner@vaultbank.com", request));
    }

    @Test
    void withdraw_WrongOwner_ThrowsException() {
        WithdrawRequest request = WithdrawRequest.builder()
                .accountNumber("123456789012")
                .amount(new BigDecimal("50.00"))
                .build();

        User owner = User.builder().id(1L).email("owner@vaultbank.com").build();
        User attacker = User.builder().id(2L).email("attacker@vaultbank.com").build();
        Account account = Account.builder()
                .accountNumber("123456789012")
                .balance(new BigDecimal("100.00"))
                .status(AccountStatus.ACTIVE)
                .user(owner)
                .build();

        when(userRepository.findByEmail("attacker@vaultbank.com")).thenReturn(Optional.of(attacker));
        when(accountRepository.findByAccountNumber(request.getAccountNumber())).thenReturn(Optional.of(account));

        assertThrows(AccessDeniedException.class, () -> transactionService.withdraw("attacker@vaultbank.com", request));
    }

    @Test
    void transfer_Success() {
        TransferRequest request = TransferRequest.builder()
                .sourceAccountNumber("111111111111")
                .destinationAccountNumber("222222222222")
                .amount(new BigDecimal("30.00"))
                .build();

        User user = User.builder().id(1L).email("owner@vaultbank.com").build();
        Account source = Account.builder()
                .accountNumber("111111111111")
                .balance(new BigDecimal("100.00"))
                .status(AccountStatus.ACTIVE)
                .user(user)
                .build();

        Account dest = Account.builder()
                .accountNumber("222222222222")
                .balance(new BigDecimal("50.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        Transaction tx = Transaction.builder()
                .transactionReference("TXN-789")
                .transactionType(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .sourceAccount(source)
                .destinationAccount(dest)
                .build();

        when(userRepository.findByEmail("owner@vaultbank.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByAccountNumber("111111111111")).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(dest));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(tx);

        TransactionResponse response = transactionService.transfer("owner@vaultbank.com", request);

        assertNotNull(response);
        assertEquals(new BigDecimal("70.00"), source.getBalance());
        assertEquals(new BigDecimal("80.00"), dest.getBalance());
        verify(kafkaProducerService, times(1)).sendTransferEvent(any(TransferEvent.class));
    }

    @Test
    void transfer_FrozenAccount_ThrowsException() {
        TransferRequest request = TransferRequest.builder()
                .sourceAccountNumber("111111111111")
                .destinationAccountNumber("222222222222")
                .amount(new BigDecimal("30.00"))
                .build();

        User user = User.builder().id(1L).email("owner@vaultbank.com").build();
        Account source = Account.builder()
                .accountNumber("111111111111")
                .balance(new BigDecimal("100.00"))
                .status(AccountStatus.FROZEN)
                .user(user)
                .build();

        Account dest = Account.builder()
                .accountNumber("222222222222")
                .balance(new BigDecimal("50.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail("owner@vaultbank.com")).thenReturn(Optional.of(user));
        when(accountRepository.findByAccountNumber("111111111111")).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(dest));

        assertThrows(AccountFrozenException.class, () -> transactionService.transfer("owner@vaultbank.com", request));
    }
}
