package com.sep.bank.common.demo;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sep.bank.account.Account;
import com.sep.bank.account.AccountRepository;
import com.sep.bank.card.Card;
import com.sep.bank.card.CardBrand;
import com.sep.bank.card.CardRepository;
import com.sep.bank.common.crypto.CardCrypto;
import com.sep.bank.customer.BankCustomer;
import com.sep.bank.customer.BankCustomerRepository;
import com.sep.bank.paymentrequest.AcquirerMerchant;
import com.sep.bank.paymentrequest.AcquirerMerchantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Test klijenti, racuni i kartice banke (iskljucivo za razvoj; DEMO_DATA_ENABLED=false iskljucuje).
 * MERCHANT_ID-jevi odgovaraju konfiguraciji metode CARD u demo podacima PSP-a.
 * Spisak test kartica je u README-u.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private final BankCustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final CardRepository cardRepository;
    private final AcquirerMerchantRepository merchantRepository;
    private final CardCrypto cardCrypto;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accountRepository.count() > 0) {
            return;
        }
        // ---------- prodavci (racuni ugovorenih trgovaca) ----------
        Account parking = account(null, "160-0000000123456-54", "Maluvufy Parking d.o.o.", "0.00");
        merchant("BNK-PSP-007781", "Maluvufy Parking d.o.o.", parking);
        Account webshop = account(null, "160-0000000654321-05", "Demo Webshop d.o.o.", "0.00");
        merchant("BNK-PSP-007782", "Demo Webshop d.o.o.", webshop);

        // ---------- kupci ----------
        BankCustomer petar = customer("petar", "Petar Petrović");
        Account petarAccount = account(petar.getId(), "160-0000000111111-35", "Petar Petrović", "50000.00");
        card(petarAccount, "4111111111111111", CardBrand.VISA, 12, 2029, "PETAR PETROVIC", "123", "ACTIVE");
        card(petarAccount, "4012888888881881", CardBrand.VISA, 1, 2025, "PETAR PETROVIC", "111", "ACTIVE");
        card(petarAccount, "2221000000000009", CardBrand.MASTERCARD, 9, 2029, "PETAR PETROVIC", "999", "BLOCKED");

        BankCustomer jelena = customer("jelena", "Jelena Jovanović");
        Account jelenaAccount = account(jelena.getId(), "160-0000000222222-91", "Jelena Jovanović", "100.00");
        card(jelenaAccount, "5555555555554444", CardBrand.MASTERCARD, 6, 2028, "JELENA JOVANOVIC", "321", "ACTIVE");

        BankCustomer milos = customer("milos", "Miloš Nikolić");
        Account milosAccount = account(milos.getId(), "160-0000000333333-50", "Miloš Nikolić", "20000.00");
        card(milosAccount, "9891000000000011", CardBrand.DINACARD, 10, 2028, "MILOS NIKOLIC", "456", "ACTIVE");

        log.warn("Ucitani demo podaci banke (DEMO_DATA_ENABLED=false za iskljucivanje)");
    }

    private BankCustomer customer(String username, String fullName) {
        BankCustomer c = new BankCustomer();
        c.setUsername(username);
        c.setFullName(fullName);
        c.setPasswordHash(passwordEncoder.encode("Kupac-2026!"));
        return customerRepository.save(c);
    }

    private Account account(UUID customerId, String number, String holder, String balance) {
        Account a = new Account();
        a.setCustomerId(customerId);
        a.setAccountNumber(number);
        a.setHolderName(holder);
        a.setBalance(new BigDecimal(balance));
        a.setReserved(BigDecimal.ZERO);
        a.setCurrency("RSD");
        return accountRepository.save(a);
    }

    private void merchant(String code, String name, Account account) {
        AcquirerMerchant m = new AcquirerMerchant();
        m.setMerchantCode(code);
        m.setName(name);
        m.setAccountId(account.getId());
        m.setStatus("ACTIVE");
        merchantRepository.save(m);
    }

    private void card(Account account, String pan, CardBrand brand, int month, int year, String holder, String cvv,
                      String status) {
        Card c = new Card();
        c.setAccountId(account.getId());
        c.setPanToken(cardCrypto.panToken(pan));
        c.setPanEncrypted(cardCrypto.encryptPan(pan));
        c.setPanLast4(pan.substring(pan.length() - 4));
        c.setBrand(brand);
        c.setExpiryMonth((short) month);
        c.setExpiryYear((short) year);
        c.setCardHolderName(holder);
        c.setCvvHash(cardCrypto.cvvHash(c.getPanToken(), cvv));
        c.setStatus(status);
        cardRepository.save(c);
    }
}
