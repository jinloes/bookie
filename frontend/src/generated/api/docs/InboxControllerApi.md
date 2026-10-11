# InboxControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                   | HTTP request                              | Description |
| ------------------------------------------------------------------------ | ----------------------------------------- | ----------- |
| [**dismissInboxItem**](InboxControllerApi.md#dismissinboxitem)           | **DELETE** /api/v2/inbox/{id}             |             |
| [**getInboxItem**](InboxControllerApi.md#getinboxitem)                   | **GET** /api/v2/inbox/{id}                |             |
| [**getInboxItemJobs**](InboxControllerApi.md#getinboxitemjobs)           | **GET** /api/v2/inbox/{id}/jobs           |             |
| [**getInboxItems**](InboxControllerApi.md#getinboxitems)                 | **GET** /api/v2/inbox                     |             |
| [**importBankInboxItems**](InboxControllerApi.md#importbankinboxitems)   | **POST** /api/v2/inbox/imports/bank       |             |
| [**importVenmoInboxItems**](InboxControllerApi.md#importvenmoinboxitems) | **POST** /api/v2/inbox/imports/venmo      |             |
| [**reparseInboxItem**](InboxControllerApi.md#reparseinboxitem)           | **POST** /api/v2/inbox/{id}/reparse       |             |
| [**retryInboxJob**](InboxControllerApi.md#retryinboxjob)                 | **POST** /api/v2/inbox/jobs/{jobId}/retry |             |
| [**saveInboxItem**](InboxControllerApi.md#saveinboxitemoperation)        | **POST** /api/v2/inbox/{id}/save          |             |
| [**subscribeInboxEvents**](InboxControllerApi.md#subscribeinboxevents)   | **GET** /api/v2/inbox/events              |             |

## dismissInboxItem

> dismissInboxItem(id)

### Example

```ts
import { Configuration, InboxControllerApi } from '';
import type { DismissInboxItemRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new InboxControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies DismissInboxItemRequest;

  try {
    const data = await api.dismissInboxItem(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name   | Type     | Description | Notes                     |
| ------ | -------- | ----------- | ------------------------- |
| **id** | `number` |             | [Defaults to `undefined`] |

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

## getInboxItem

> InboxItemResponse getInboxItem(id)

### Example

```ts
import { Configuration, InboxControllerApi } from '';
import type { GetInboxItemRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new InboxControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetInboxItemRequest;

  try {
    const data = await api.getInboxItem(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name   | Type     | Description | Notes                     |
| ------ | -------- | ----------- | ------------------------- |
| **id** | `number` |             | [Defaults to `undefined`] |

### Return type

[**InboxItemResponse**](InboxItemResponse.md)

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

## getInboxItemJobs

> Array&lt;BackgroundJobResponse&gt; getInboxItemJobs(id)

### Example

```ts
import { Configuration, InboxControllerApi } from '';
import type { GetInboxItemJobsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new InboxControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetInboxItemJobsRequest;

  try {
    const data = await api.getInboxItemJobs(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name   | Type     | Description | Notes                     |
| ------ | -------- | ----------- | ------------------------- |
| **id** | `number` |             | [Defaults to `undefined`] |

### Return type

[**Array&lt;BackgroundJobResponse&gt;**](BackgroundJobResponse.md)

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

## getInboxItems

> Array&lt;InboxItemResponse&gt; getInboxItems()

### Example

```ts
import { Configuration, InboxControllerApi } from '';
import type { GetInboxItemsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new InboxControllerApi();

  try {
    const data = await api.getInboxItems();
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

[**Array&lt;InboxItemResponse&gt;**](InboxItemResponse.md)

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

## importBankInboxItems

> BankImportResponse importBankInboxItems(file, activityId)

### Example

```ts
import { Configuration, InboxControllerApi } from '';
import type { ImportBankInboxItemsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new InboxControllerApi();

  const body = {
    // Blob
    file: BINARY_DATA_HERE,
    // number (optional)
    activityId: 789,
  } satisfies ImportBankInboxItemsRequest;

  try {
    const data = await api.importBankInboxItems(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name           | Type     | Description | Notes                                |
| -------------- | -------- | ----------- | ------------------------------------ |
| **file**       | `Blob`   |             | [Defaults to `undefined`]            |
| **activityId** | `number` |             | [Optional] [Defaults to `undefined`] |

### Return type

[**BankImportResponse**](BankImportResponse.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `multipart/form-data`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## importVenmoInboxItems

> VenmoImportResponse importVenmoInboxItems(file, counterpartyId, activityId, propertyId)

### Example

```ts
import { Configuration, InboxControllerApi } from '';
import type { ImportVenmoInboxItemsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new InboxControllerApi();

  const body = {
    // Blob
    file: BINARY_DATA_HERE,
    // number (optional)
    counterpartyId: 789,
    // number (optional)
    activityId: 789,
    // number (optional)
    propertyId: 789,
  } satisfies ImportVenmoInboxItemsRequest;

  try {
    const data = await api.importVenmoInboxItems(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name               | Type     | Description | Notes                                |
| ------------------ | -------- | ----------- | ------------------------------------ |
| **file**           | `Blob`   |             | [Defaults to `undefined`]            |
| **counterpartyId** | `number` |             | [Optional] [Defaults to `undefined`] |
| **activityId**     | `number` |             | [Optional] [Defaults to `undefined`] |
| **propertyId**     | `number` |             | [Optional] [Defaults to `undefined`] |

### Return type

[**VenmoImportResponse**](VenmoImportResponse.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `multipart/form-data`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## reparseInboxItem

> InboxItemResponse reparseInboxItem(id)

### Example

```ts
import { Configuration, InboxControllerApi } from '';
import type { ReparseInboxItemRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new InboxControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies ReparseInboxItemRequest;

  try {
    const data = await api.reparseInboxItem(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name   | Type     | Description | Notes                     |
| ------ | -------- | ----------- | ------------------------- |
| **id** | `number` |             | [Defaults to `undefined`] |

### Return type

[**InboxItemResponse**](InboxItemResponse.md)

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

## retryInboxJob

> BackgroundJobResponse retryInboxJob(jobId)

### Example

```ts
import { Configuration, InboxControllerApi } from '';
import type { RetryInboxJobRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new InboxControllerApi();

  const body = {
    // number
    jobId: 789,
  } satisfies RetryInboxJobRequest;

  try {
    const data = await api.retryInboxJob(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name      | Type     | Description | Notes                     |
| --------- | -------- | ----------- | ------------------------- |
| **jobId** | `number` |             | [Defaults to `undefined`] |

### Return type

[**BackgroundJobResponse**](BackgroundJobResponse.md)

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

## saveInboxItem

> InboxItemResponse saveInboxItem(id, saveInboxItemRequest)

### Example

```ts
import {
  Configuration,
  InboxControllerApi,
} from '';
import type { SaveInboxItemOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new InboxControllerApi();

  const body = {
    // number
    id: 789,
    // SaveInboxItemRequest
    saveInboxItemRequest: ...,
  } satisfies SaveInboxItemOperationRequest;

  try {
    const data = await api.saveInboxItem(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                     | Type                                            | Description | Notes                     |
| ------------------------ | ----------------------------------------------- | ----------- | ------------------------- |
| **id**                   | `number`                                        |             | [Defaults to `undefined`] |
| **saveInboxItemRequest** | [SaveInboxItemRequest](SaveInboxItemRequest.md) |             |                           |

### Return type

[**InboxItemResponse**](InboxItemResponse.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## subscribeInboxEvents

> SseEmitter subscribeInboxEvents()

### Example

```ts
import { Configuration, InboxControllerApi } from '';
import type { SubscribeInboxEventsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new InboxControllerApi();

  try {
    const data = await api.subscribeInboxEvents();
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

[**SseEmitter**](SseEmitter.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`, `text/event-stream`

### HTTP response details

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
