# ReceiptControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                     | HTTP request                            | Description |
| -------------------------------------------------------------------------- | --------------------------------------- | ----------- |
| [**deleteReceipt**](ReceiptControllerApi.md#deletereceipt)                 | **DELETE** /api/receipts/{itemId}       |             |
| [**downloadReceipt**](ReceiptControllerApi.md#downloadreceipt)             | **GET** /api/receipts/{itemId}/download |             |
| [**getReceiptSettings**](ReceiptControllerApi.md#getreceiptsettings)       | **GET** /api/receipts/settings          |             |
| [**getReceipts**](ReceiptControllerApi.md#getreceipts)                     | **GET** /api/receipts                   |             |
| [**parseReceipt**](ReceiptControllerApi.md#parsereceipt)                   | **POST** /api/receipts/{itemId}/parse   |             |
| [**updateReceiptSettings**](ReceiptControllerApi.md#updatereceiptsettings) | **PUT** /api/receipts/settings          |             |
| [**uploadReceipt**](ReceiptControllerApi.md#uploadreceipt)                 | **POST** /api/receipts/upload           |             |

## deleteReceipt

> deleteReceipt(itemId)

### Example

```ts
import { Configuration, ReceiptControllerApi } from '';
import type { DeleteReceiptRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ReceiptControllerApi();

  const body = {
    // string
    itemId: itemId_example,
  } satisfies DeleteReceiptRequest;

  try {
    const data = await api.deleteReceipt(body);
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
| **itemId** | `string` |             | [Defaults to `undefined`] |

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
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## downloadReceipt

> Blob downloadReceipt(itemId)

### Example

```ts
import { Configuration, ReceiptControllerApi } from '';
import type { DownloadReceiptRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ReceiptControllerApi();

  const body = {
    // string
    itemId: itemId_example,
  } satisfies DownloadReceiptRequest;

  try {
    const data = await api.downloadReceipt(body);
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
| **itemId** | `string` |             | [Defaults to `undefined`] |

### Return type

**Blob**

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

## getReceiptSettings

> { [key: string]: any; } getReceiptSettings()

### Example

```ts
import { Configuration, ReceiptControllerApi } from '';
import type { GetReceiptSettingsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ReceiptControllerApi();

  try {
    const data = await api.getReceiptSettings();
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

**{ [key: string]: any; }**

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

## getReceipts

> Array&lt;ReceiptDto&gt; getReceipts()

### Example

```ts
import { Configuration, ReceiptControllerApi } from '';
import type { GetReceiptsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ReceiptControllerApi();

  try {
    const data = await api.getReceipts();
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

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## parseReceipt

> { [key: string]: any; } parseReceipt(itemId)

### Example

```ts
import { Configuration, ReceiptControllerApi } from '';
import type { ParseReceiptRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ReceiptControllerApi();

  const body = {
    // string
    itemId: itemId_example,
  } satisfies ParseReceiptRequest;

  try {
    const data = await api.parseReceipt(body);
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
| **itemId** | `string` |             | [Defaults to `undefined`] |

### Return type

**{ [key: string]: any; }**

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

## updateReceiptSettings

> { [key: string]: any; } updateReceiptSettings(requestBody)

### Example

```ts
import { Configuration, ReceiptControllerApi } from '';
import type { UpdateReceiptSettingsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ReceiptControllerApi();

  const body = {
    // { [key: string]: any; }
    requestBody: Object,
  } satisfies UpdateReceiptSettingsRequest;

  try {
    const data = await api.updateReceiptSettings(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name            | Type                      | Description | Notes |
| --------------- | ------------------------- | ----------- | ----- |
| **requestBody** | `{ [key: string]: any; }` |             |       |

### Return type

**{ [key: string]: any; }**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## uploadReceipt

> UploadReceiptResponse uploadReceipt(file)

### Example

```ts
import { Configuration, ReceiptControllerApi } from '';
import type { UploadReceiptRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ReceiptControllerApi();

  const body = {
    // Blob
    file: BINARY_DATA_HERE,
  } satisfies UploadReceiptRequest;

  try {
    const data = await api.uploadReceipt(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name     | Type   | Description | Notes                     |
| -------- | ------ | ----------- | ------------------------- |
| **file** | `Blob` |             | [Defaults to `undefined`] |

### Return type

[**UploadReceiptResponse**](UploadReceiptResponse.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `multipart/form-data`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
