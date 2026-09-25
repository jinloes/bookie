package com.bookie.intake.sources;

public record UploadReceiptResponse(ReceiptDto receipt, boolean duplicate) {}
