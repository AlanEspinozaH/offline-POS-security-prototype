package com.mycompany.pepitoapp.security.ledger;

import java.util.List;

public record VerificationResult(boolean valid, List<String> errors) {}
