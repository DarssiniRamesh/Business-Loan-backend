#!/bin/bash
cd /home/kavia/workspace/code-generation/Business-Loan-backend/BusinessLoanAPI(SpringBoot)
./gradlew checkstyleMain
LINT_EXIT_CODE=$?
if [ $LINT_EXIT_CODE -ne 0 ]; then
   exit 1
fi

