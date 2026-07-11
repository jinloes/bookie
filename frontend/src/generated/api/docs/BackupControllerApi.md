# BackupControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                    | HTTP request                          | Description |
| --------------------------------------------------------- | ------------------------------------- | ----------- |
| [**createBackup**](BackupControllerApi.md#createbackup)   | **POST** /api/backup                  |             |
| [**deleteBackup**](BackupControllerApi.md#deletebackup)   | **DELETE** /api/backup/{fileId}       |             |
| [**getBackups**](BackupControllerApi.md#getbackups)       | **GET** /api/backup/list              |             |
| [**restoreBackup**](BackupControllerApi.md#restorebackup) | **POST** /api/backup/restore/{fileId} |             |

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
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
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
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **204**     | No Content            | -                |

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
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

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
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
