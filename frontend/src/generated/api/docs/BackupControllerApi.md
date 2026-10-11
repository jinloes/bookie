# BackupControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                          | HTTP request                          | Description |
| ------------------------------------------------------------------------------- | ------------------------------------- | ----------- |
| [**confirmBackupKey**](BackupControllerApi.md#confirmbackupkey)                 | **POST** /api/backups/key/confirm     |             |
| [**createBackup**](BackupControllerApi.md#createbackup)                         | **POST** /api/backup                  |             |
| [**createPreMigrationBackup**](BackupControllerApi.md#createpremigrationbackup) | **POST** /api/backups/pre-migration   |             |
| [**createRollbackBackup**](BackupControllerApi.md#createrollbackbackup)         | **POST** /api/backups/rollback        |             |
| [**deleteBackup**](BackupControllerApi.md#deletebackup)                         | **DELETE** /api/backup/{fileId}       |             |
| [**getBackupStatus**](BackupControllerApi.md#getbackupstatus)                   | **GET** /api/backups/status           |             |
| [**getBackups**](BackupControllerApi.md#getbackups)                             | **GET** /api/backup/list              |             |
| [**getRestoreStatus**](BackupControllerApi.md#getrestorestatus)                 | **GET** /api/backup/restore/status    |             |
| [**liftWriteFreeze**](BackupControllerApi.md#liftwritefreeze)                   | **DELETE** /api/backups/freeze        |             |
| [**restoreBackup**](BackupControllerApi.md#restorebackup)                       | **POST** /api/backup/restore/{fileId} |             |
| [**revealBackupKey**](BackupControllerApi.md#revealbackupkey)                   | **POST** /api/backups/key/reveal      |             |

## confirmBackupKey

> confirmBackupKey()

### Example

```ts
import { Configuration, BackupControllerApi } from '';
import type { ConfirmBackupKeyRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new BackupControllerApi();

  try {
    const data = await api.confirmBackupKey();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

`void` (Empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **204**     | No Content            | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## createBackup

> BackupFile createBackup()

### Example

```ts
import { Configuration, BackupControllerApi } from '';
import type { CreateBackupRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new BackupControllerApi();

  try {
    const data = await api.createBackup();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**BackupFile**](BackupFile.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## createPreMigrationBackup

> PreMigrationResult createPreMigrationBackup()

### Example

```ts
import { Configuration, BackupControllerApi } from '';
import type { CreatePreMigrationBackupRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new BackupControllerApi();

  try {
    const data = await api.createPreMigrationBackup();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**PreMigrationResult**](PreMigrationResult.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## createRollbackBackup

> BackupFile createRollbackBackup()

### Example

```ts
import { Configuration, BackupControllerApi } from '';
import type { CreateRollbackBackupRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new BackupControllerApi();

  try {
    const data = await api.createRollbackBackup();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**BackupFile**](BackupFile.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## deleteBackup

> deleteBackup(fileId)

### Example

```ts
import { Configuration, BackupControllerApi } from '';
import type { DeleteBackupRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new BackupControllerApi();

  const body = {
    // string
    fileId: fileId_example,
  } satisfies DeleteBackupRequest;

  try {
    const data = await api.deleteBackup(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name       | Type     | Description | Notes                     |
| ---------- | -------- | ----------- | ------------------------- |
| **fileId** | `string` |             | [Defaults to `undefined`] |

### Return type

`void` (Empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **204**     | No Content            | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getBackupStatus

> BackupStatus getBackupStatus()

### Example

```ts
import { Configuration, BackupControllerApi } from '';
import type { GetBackupStatusRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new BackupControllerApi();

  try {
    const data = await api.getBackupStatus();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**BackupStatus**](BackupStatus.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getBackups

> Array&lt;BackupFile&gt; getBackups()

### Example

```ts
import { Configuration, BackupControllerApi } from '';
import type { GetBackupsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new BackupControllerApi();

  try {
    const data = await api.getBackups();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**Array&lt;BackupFile&gt;**](BackupFile.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getRestoreStatus

> RestoreResult getRestoreStatus()

### Example

```ts
import { Configuration, BackupControllerApi } from '';
import type { GetRestoreStatusRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new BackupControllerApi();

  try {
    const data = await api.getRestoreStatus();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**RestoreResult**](RestoreResult.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## liftWriteFreeze

> liftWriteFreeze()

### Example

```ts
import { Configuration, BackupControllerApi } from '';
import type { LiftWriteFreezeRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new BackupControllerApi();

  try {
    const data = await api.liftWriteFreeze();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

`void` (Empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **204**     | No Content            | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## restoreBackup

> RestoreResult restoreBackup(fileId)

### Example

```ts
import { Configuration, BackupControllerApi } from '';
import type { RestoreBackupRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new BackupControllerApi();

  const body = {
    // string
    fileId: fileId_example,
  } satisfies RestoreBackupRequest;

  try {
    const data = await api.restoreBackup(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name       | Type     | Description | Notes                     |
| ---------- | -------- | ----------- | ------------------------- |
| **fileId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**RestoreResult**](RestoreResult.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## revealBackupKey

> BackupKey revealBackupKey()

### Example

```ts
import { Configuration, BackupControllerApi } from '';
import type { RevealBackupKeyRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new BackupControllerApi();

  try {
    const data = await api.revealBackupKey();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**BackupKey**](BackupKey.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
