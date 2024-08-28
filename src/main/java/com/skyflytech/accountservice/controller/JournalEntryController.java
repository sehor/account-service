package com.skyflytech.accountservice.controller;

import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntryView;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.service.JournalEntryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    @Value("${spring.profiles.active}")
    private String activeProfile;

    @Autowired
    public JournalEntryController(JournalEntryService journalEntryService, CurrentAccountSetIdHolder currentAccountSetIdHolder) {
        this.journalEntryService = journalEntryService;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
    }

    @PostMapping("/process")
    public ResponseEntity<JournalEntryView> processJournalEntryView(@RequestBody JournalEntryView journalEntryView) {
        try {
            return ResponseEntity.ok().body(journalEntryService.processJournalEntryView(journalEntryView));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
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
        journalEntryService.deleteAllEntry(currentAccountSetIdHolder.getCurrentAccountSetId());
        return ResponseEntity.ok("deleted  records successfully!");
    }catch (Exception e){
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(e.getMessage());
    }



    }
}
