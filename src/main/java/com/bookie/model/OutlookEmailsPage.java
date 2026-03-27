package com.bookie.model;

import java.util.List;

public record OutlookEmailsPage(List<OutlookEmail> emails, int page, boolean hasMore) {
}