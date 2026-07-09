# BackupControllerApi

All URIs are relative to *http://localhost:48763*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**backup**](BackupControllerApi.md#backup) | **POST** /api/backup |  |
| [**delete5**](BackupControllerApi.md#delete5) | **DELETE** /api/backup/{fileId} |  |
| [**listBackups**](BackupControllerApi.md#listbackups) | **GET** /api/backup/list |  |
| [**restore**](BackupControllerApi.md#restore) | **POST** /api/backup/restore/{fileId} |  |



## backup

> BackupFile backup()



### Example

```ts
import {
  Configuration,
  BackupControllerApi,
} from '';
import type { BackupRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new BackupControllerApi();

  try {
    const data = await api.backup();
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
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## delete5

> delete5(fileId)



### Example

```ts
import {
  Configuration,
  BackupControllerApi,
} from '';
import type { Delete5Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new BackupControllerApi();

  const body = {
    // string
    fileId: fileId_example,
  } satisfies Delete5Request;

  try {
    const data = await api.delete5(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **fileId** | `string` |  | [Defaults to `undefined`] |

### Return type

`void` (Empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **204** | No Content |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listBackups

> Array&lt;BackupFile&gt; listBackups()



### Example

```ts
import {
  Configuration,
  BackupControllerApi,
} from '';
import type { ListBackupsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new BackupControllerApi();

  try {
    const data = await api.listBackups();
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
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## restore

> RestoreResult restore(fileId)



### Example

```ts
import {
  Configuration,
  BackupControllerApi,
} from '';
import type { RestoreRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new BackupControllerApi();

  const body = {
    // string
    fileId: fileId_example,
  } satisfies RestoreRequest;

  try {
    const data = await api.restore(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **fileId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**RestoreResult**](RestoreResult.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

