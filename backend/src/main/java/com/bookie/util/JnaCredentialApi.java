package com.bookie.util;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.util.Optional;

/** {@link WindowsCredentialStore.CredentialApi} backed by {@code advapi32.dll} through JNA. */
class JnaCredentialApi implements WindowsCredentialStore.CredentialApi {

  static final int CRED_TYPE_GENERIC = 1;
  static final int CRED_PERSIST_LOCAL_MACHINE = 2;

  interface Advapi32 extends StdCallLibrary {
    boolean CredReadW(WString targetName, int type, int flags, PointerByReference credential);

    boolean CredWriteW(Credential credential, int flags);

    void CredFree(Pointer buffer);
  }

  /** {@code CREDENTIALW}; FILETIME is split into its two DWORDs to keep the native alignment. */
  @Structure.FieldOrder({
    "flags",
    "type",
    "targetName",
    "comment",
    "lastWrittenLow",
    "lastWrittenHigh",
    "credentialBlobSize",
    "credentialBlob",
    "persist",
    "attributeCount",
    "attributes",
    "targetAlias",
    "userName"
  })
  public static class Credential extends Structure {
    public int flags;
    public int type;
    public WString targetName;
    public WString comment;
    public int lastWrittenLow;
    public int lastWrittenHigh;
    public int credentialBlobSize;
    public Pointer credentialBlob;
    public int persist;
    public int attributeCount;
    public Pointer attributes;
    public WString targetAlias;
    public WString userName;

    public Credential() {
      super();
    }

    Credential(Pointer pointer) {
      super(pointer);
      read();
    }
  }

  private Advapi32 library;

  // Loaded lazily so the class can be constructed on machines without advapi32.
  private synchronized Advapi32 library() {
    if (library == null) {
      library = Native.load("Advapi32", Advapi32.class);
    }
    return library;
  }

  @Override
  public Optional<byte[]> read(String targetName) {
    Advapi32 advapi32 = library();
    PointerByReference reference = new PointerByReference();
    if (!advapi32.CredReadW(new WString(targetName), CRED_TYPE_GENERIC, 0, reference)) {
      return Optional.empty();
    }
    try {
      Credential credential = new Credential(reference.getValue());
      if (credential.credentialBlob == null || credential.credentialBlobSize <= 0) {
        return Optional.empty();
      }
      return Optional.of(credential.credentialBlob.getByteArray(0, credential.credentialBlobSize));
    } finally {
      advapi32.CredFree(reference.getValue());
    }
  }

  @Override
  public boolean write(String targetName, String userName, byte[] secret) {
    try (Memory blob = new Memory(secret.length)) {
      blob.write(0, secret, 0, secret.length);
      Credential credential = new Credential();
      credential.type = CRED_TYPE_GENERIC;
      credential.targetName = new WString(targetName);
      credential.userName = new WString(userName);
      credential.persist = CRED_PERSIST_LOCAL_MACHINE;
      credential.credentialBlobSize = secret.length;
      credential.credentialBlob = blob;
      try {
        return library().CredWriteW(credential, 0);
      } finally {
        blob.clear();
      }
    }
  }
}
