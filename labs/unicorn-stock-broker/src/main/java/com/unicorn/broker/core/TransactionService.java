package com.unicorn.broker.core;

import com.unicorn.broker.data.BlockedStockFetcher;
import com.unicorn.broker.data.TransactionRepository;
import com.unicorn.broker.exceptions.InvalidStockException;
import com.unicorn.broker.model.Transaction;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionService implements Resource {

    private UUID BROKER_ID;
    private final TransactionRepository transactionRepository;
    private final Logger logger = LoggerFactory.getLogger(TransactionService.class);
    private final BlockedStockFetcher blockedStockFetcher;


    public TransactionService(final TransactionRepository transactionRepository, final BlockedStockFetcher blockedStockFetcher) {
        Core.getGlobalContext().register(this);
        this.blockedStockFetcher = blockedStockFetcher;
        this.transactionRepository = transactionRepository;
    }

    public Optional<Transaction> writeTransaction(Transaction transaction) {
        if(blockedStockFetcher.getBlockedStocks().contains(transaction.stockId)) {
            throw new InvalidStockException(transaction.stockId + " is not a valid stock.");
        }
        try {
            Thread.sleep(100); //Simulates some intensive calculation
            transaction.transactionId = UUID.randomUUID();
            transaction.brokerId = BROKER_ID;

            return transactionRepository.writeTransaction(transaction);
        } catch (InterruptedException e) {
            logger.error("Error due to interruption while processing the transaction.", e);
            return Optional.empty();
        }
    }

    //3. Implement the before checkpoint hook
    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
        logger.info("Hello from before checkpoint hook");    
    }

    //4. Implement the after restore hook
    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        logger.info("Hello from afterRestore hook");
        BROKER_ID = UUID.randomUUID();
    }
}
