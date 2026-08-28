package com.bookie.integrations.venmo;

import java.io.IOException;

public interface VenmoInputPort {

  VenmoStatement parse(byte[] csvBytes) throws IOException;
}
