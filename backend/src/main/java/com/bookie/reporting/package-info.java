/** Financial reports built from ledger query and catalog report-policy ports. */
@ApplicationModule(allowedDependencies = {"catalog :: *", "ledger :: *"})
package com.bookie.reporting;

import org.springframework.modulith.ApplicationModule;
