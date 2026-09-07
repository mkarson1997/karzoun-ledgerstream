package io.karzoun.ledgerstream.service;

import java.util.UUID;

public record PostingResult(UUID entryId, long streamVersion, boolean idempotentReplay) { }
