/** Durable financial intake workflow. */
@ApplicationModule(
    allowedDependencies = {
      "catalog :: *",
      "ledger :: *",
      "integrations",
      "integrations :: *",
      "util"
    })
package com.bookie.intake;

import org.springframework.modulith.ApplicationModule;
