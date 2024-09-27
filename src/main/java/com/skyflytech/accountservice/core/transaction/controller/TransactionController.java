package com.skyflytech.accountservice.core.transaction.controller;

import com.mongodb.client.result.DeleteResult;
import com.skyflytech.accountservice.core.transaction.model.Transaction;
import com.skyflytech.accountservice.security.model.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.core.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * @Author pzr
 * @date:2024-08-16-8:42
 * @Description:
 **/
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transaction API", description = "API for managing transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;
    private final MongoOperations mongoOperations;

    @Value("${spring.profiles.active}")
    private String activeProfile;
    @Autowired
    public TransactionController(TransactionService transactionService, MongoOperations mongoOperations, CurrentAccountSetIdHolder currentAccountSetIdHolder) {
        this.transactionService = transactionService;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
        this.mongoOperations = mongoOperations;
    }

    // Get all transactions
    @Operation(summary = "Get all transactions", description = "Retrieve a list of all transactions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Transaction.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/all")
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        List<Transaction> transactions = transactionService.getAllTransactions(currentAccountSetIdHolder.getCurrentAccountSetId());
        return ResponseEntity.ok(transactions);
    }

    // Search transactions by description (fuzzy search)
    @Operation(summary = "Search transactions", description = "Search transactions by description (fuzzy search)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Transaction.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search query", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/search")
    public ResponseEntity<List<Transaction>> searchTransactions(@RequestParam("query") String query) {
        List<Transaction> transactions = transactionService.searchTransactions(currentAccountSetIdHolder.getCurrentAccountSetId(),query);
        return ResponseEntity.ok(transactions);
    }

    @Operation(summary = "Get transactions by account ID", description = "Returns a list of transactions associated with the provided account ID.")
    @GetMapping("/{accountId}")
    public ResponseEntity<List<Transaction>> getTransactionsByAccountId(@PathVariable String accountId) {
        List<Transaction> transactions = transactionService.findByAccountId(accountId);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/period/{accountId}")
    public ResponseEntity<PagedModel<EntityModel<Transaction>>> getTransactionsByAccountAndPeriod(
            @PathVariable String accountId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            PagedResourcesAssembler<Transaction> assembler) {

        Page<Transaction> transactions = transactionService.findTransactionsByAccountAndPeriod(accountId, startDate, endDate, page, size);
        PagedModel<EntityModel<Transaction>> pagedModel = assembler.toModel(transactions);

        return ResponseEntity.ok(pagedModel);
    }
    @GetMapping("/beforeTotals/{accountId}")
    public ResponseEntity<Map.Entry<BigDecimal, BigDecimal>> getTotalDebitAndCredit(
            @PathVariable String accountId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate modifiedDate) {

        Map.Entry<BigDecimal, BigDecimal> totals = transactionService.calculateTotalDebitAndCredit(accountId, modifiedDate);
        return ResponseEntity.ok(totals);
    }


    //warn : just for developing stage and test
    @PostMapping("/delete/all")
    public ResponseEntity<String> deleteAll() {
        if ("prod".equals(activeProfile)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        DeleteResult result = mongoOperations.remove(new Query(), Transaction.class);
        long deletedCount = result.getDeletedCount();
        return ResponseEntity.ok(String.format("deleted %d records successfully!",deletedCount));
    }
}
