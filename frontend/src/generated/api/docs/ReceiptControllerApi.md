# ReceiptControllerApi

All URIs are relative to *http://localhost:48763*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**delete4**](ReceiptControllerApi.md#delete4) | **DELETE** /api/receipts/{itemId} |  |
| [**download**](ReceiptControllerApi.md#download) | **GET** /api/receipts/{itemId}/download |  |
| [**getSettings**](ReceiptControllerApi.md#getsettings) | **GET** /api/receipts/settings |  |
| [**listReceipts**](ReceiptControllerApi.md#listreceipts) | **GET** /api/receipts |  |
| [**parse**](ReceiptControllerApi.md#parse) | **POST** /api/receipts/{itemId}/parse |  |
| [**updateSettings**](ReceiptControllerApi.md#updatesettings) | **PUT** /api/receipts/settings |  |
| [**upload**](ReceiptControllerApi.md#upload) | **POST** /api/receipts/upload |  |



## delete4

> delete4(itemId)



### Example

```ts
import {
  Configuration,
  ReceiptControllerApi,
} from '';
import type { Delete4Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ReceiptControllerApi();

  const body = {
    // string
    itemId: itemId_example,
  } satisfies Delete4Request;

  try {
    const data = await api.delete4(body);
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
| **itemId** | `string` |  | [Defaults to `undefined`] |

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
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## download

> Blob download(itemId)



### Example

```ts
import {
  Configuration,
  ReceiptControllerApi,
} from '';
import type { DownloadRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ReceiptControllerApi();

  const body = {
    // string
    itemId: itemId_example,
  } satisfies DownloadRequest;

  try {
    const data = await api.download(body);
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
| **itemId** | `string` |  | [Defaults to `undefined`] |

### Return type

**Blob**

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


## getSettings

> { [key: string]: string; } getSettings()



### Example

```ts
import {
  Configuration,
  ReceiptControllerApi,
} from '';
import type { GetSettingsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ReceiptControllerApi();

  try {
    const data = await api.getSettings();
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

**{ [key: string]: string; }**

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


## listReceipts

> Array&lt;ReceiptDto&gt; listReceipts()



### Example

```ts
import {
  Configuration,
  ReceiptControllerApi,
} from '';
import type { ListReceiptsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ReceiptControllerApi();

  try {
    const data = await api.listReceipts();
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

[**Array&lt;ReceiptDto&gt;**](ReceiptDto.md)

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


## parse

> { [key: string]: any; } parse(itemId)



### Example

```ts
import {
  Configuration,
  ReceiptControllerApi,
} from '';
import type { ParseRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ReceiptControllerApi();

  const body = {
    // string
    itemId: itemId_example,
  } satisfies ParseRequest;

  try {
    const data = await api.parse(body);
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
| **itemId** | `string` |  | [Defaults to `undefined`] |

### Return type

**{ [key: string]: any; }**

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


## updateSettings

> { [key: string]: string; } updateSettings(requestBody)



### Example

```ts
import {
  Configuration,
  ReceiptControllerApi,
} from '';
import type { UpdateSettingsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ReceiptControllerApi();

  const body = {
    // { [key: string]: string; }
    requestBody: Object,
  } satisfies UpdateSettingsRequest;

  try {
    const data = await api.updateSettings(body);
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
| **requestBody** | `{ [key: string]: string; }` |  | |

### Return type

**{ [key: string]: string; }**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## upload

> UploadReceiptResponse upload(file)



### Example

```ts
import {
  Configuration,
  ReceiptControllerApi,
} from '';
import type { UploadRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ReceiptControllerApi();

  const body = {
    // Blob
    file: BINARY_DATA_HERE,
  } satisfies UploadRequest;

  try {
    const data = await api.upload(body);
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
| **file** | `Blob` |  | [Defaults to `undefined`] |

### Return type

[**UploadReceiptResponse**](UploadReceiptResponse.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `multipart/form-data`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

