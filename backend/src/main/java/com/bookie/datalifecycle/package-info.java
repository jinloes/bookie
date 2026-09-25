/** Backup, restore and migration-integrity lifecycle of the local database. */
@ApplicationModule(allowedDependencies = {"integrations", "integrations :: *"})
package com.bookie.datalifecycle;

import org.springframework.modulith.ApplicationModule;
