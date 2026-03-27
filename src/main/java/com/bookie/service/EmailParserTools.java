package com.bookie.service;

import com.bookie.model.ExpenseCategory;
import com.bookie.model.Payer;
import com.bookie.model.Property;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EmailParserTools {

    private final PayerRepository payerRepository;
    private final PropertyRepository propertyRepository;

    @Tool(description = "Returns the valid expense categories. Call this to pick the most appropriate category for the expense.")
    public List<String> getExpenseCategories() {
        return Arrays.stream(ExpenseCategory.values())
                .map(c -> "%s (Schedule E line %d)".formatted(c.name(), c.scheduleELine))
                .toList();
    }

    @Tool(description = "Returns the list of known payers (people or companies expenses are paid to). "
            + "Call this to check whether a payer name found in the email matches an existing payer.")
    public List<String> getKnownPayers() {
        return payerRepository.findAll().stream()
                .map(Payer::getName)
                .toList();
    }

    @Tool(description = "Returns the list of known rental properties with their addresses. "
            + "Call this to check whether a service address or property name found in the email matches a known property. "
            + "Format: 'Property Name (address)' or just 'Property Name' if no address is set.")
    public List<String> getKnownProperties() {
        return propertyRepository.findAll().stream()
                .map(p -> p.getAddress() != null && !p.getAddress().isBlank()
                        ? "%s (%s)".formatted(p.getName(), p.getAddress())
                        : p.getName())
                .toList();
    }
}