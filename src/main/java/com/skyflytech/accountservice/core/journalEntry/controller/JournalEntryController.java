package com.skyflytech.accountservice.core.journalEntry.controller;

import com.skyflytech.accountservice.core.journalEntry.model.JournalEntry;
import com.skyflytech.accountservice.core.journalEntry.model.JournalEntryView;
import com.skyflytech.accountservice.security.model.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.core.journalEntry.service.JournalEntryService;
import com.skyflytech.accountservice.core.journalEntry.service.ProcessJournalEntry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * @Author pzr
 * @date:2024-08-16-16:21
 * @Description:
 **/
@RestController
@RequestMapping("/api/journalEntries")
public class JournalEntryController {

    private final JournalEntryService journalEntryService;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;
    private final ProcessJournalEntry processJournalEntryView;
    @Value("${spring.profiles.active}")
    private String activeProfile;

    @Autowired
    public JournalEntryController(JournalEntryService journalEntryService, CurrentAccountSetIdHolder currentAccountSetIdHolder,ProcessJournalEntry processJournalEntryView) {
        this.journalEntryService = journalEntryService;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
        this.processJournalEntryView = processJournalEntryView;
    }

    @PostMapping("/process")
    public ResponseEntity<?> processJournalEntryView(@RequestBody JournalEntryView journalEntryView) {
        try {
            return ResponseEntity.ok().body(processJournalEntryView.processJournalEntryView(journalEntryView));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<JournalEntry>> allJournalEntry(){

        return ResponseEntity.ok().body(journalEntryService.getAllJournalEntries(currentAccountSetIdHolder.getCurrentAccountSetId()));
    }

    //warn : just for developing stage and test
    @PostMapping("/delete/all")
    public ResponseEntity<String> deleteAll() {
        if ("prod".equals(activeProfile)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    try {
        journalEntryService.deleteJournalEntriesByAccountSetId(currentAccountSetIdHolder.getCurrentAccountSetId());
        return ResponseEntity.ok("deleted  records successfully!");
    }catch (Exception e){
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(e.getMessage());
    }



    }

    @GetMapping("/byDateRange")
    public ResponseEntity<?> getJournalEntriesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            String accountSetId = currentAccountSetIdHolder.getCurrentAccountSetId();
            List<JournalEntryView> entries = journalEntryService.getJournalEntriesByPeriod(accountSetId, startDate, endDate);
            return ResponseEntity.ok(entries);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

}
