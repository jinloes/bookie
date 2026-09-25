package com.bookie.intake.sources;

import java.util.List;

public record OutlookEmailsPage(List<OutlookEmail> emails, int page, boolean hasMore) {}
