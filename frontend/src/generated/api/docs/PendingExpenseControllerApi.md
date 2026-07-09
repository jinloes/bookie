# PendingExpenseControllerApi

All URIs are relative to *http://localhost:48763*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**dismiss**](PendingExpenseControllerApi.md#dismiss) | **DELETE** /api/pending-expenses/{id} |  |
| [**list**](PendingExpenseControllerApi.md#list) | **GET** /api/pending-expenses |  |
| [**retry**](PendingExpenseControllerApi.md#retry) | **POST** /api/pending-expenses/{id}/retry |  |
| [**save**](PendingExpenseControllerApi.md#save) | **POST** /api/pending-expenses/{id}/save |  |
| [**saveAsIncome**](PendingExpenseControllerApi.md#saveasincome) | **POST** /api/pending-expenses/{id}/save-income |  |
| [**subscribe**](PendingExpenseControllerApi.md#subscribe) | **GET** /api/pending-expenses/events |  |



## dismiss

> dismiss(id)



### Example

```ts
import {
  Configuration,
  PendingExpenseControllerApi,
} from '';
import type { DismissRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PendingExpenseControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies DismissRequest;

  try {
    const data = await api.dismiss(body);
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
| **id** | `number` |  | [Defaults to `undefined`] |

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


## list

> Array&lt;PendingExpenseResponse&gt; list()



### Example

```ts
import {
  Configuration,
  PendingExpenseControllerApi,
} from '';
import type { ListRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PendingExpenseControllerApi();

  try {
    const data = await api.list();
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

[**Array&lt;PendingExpenseResponse&gt;**](PendingExpenseResponse.md)

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


## retry

> retry(id)



### Example

```ts
import {
  Configuration,
  PendingExpenseControllerApi,
} from '';
import type { RetryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PendingExpenseControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies RetryRequest;

  try {
    const data = await api.retry(body);
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
| **id** | `number` |  | [Defaults to `undefined`] |

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
| **202** | Accepted |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## save

> ExpenseResponse save(id, savePendingExpenseRequest)



### Example

```ts
import {
  Configuration,
  PendingExpenseControllerApi,
} from '';
import type { SaveRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PendingExpenseControllerApi();

  const body = {
    // number
    id: 789,
    // SavePendingExpenseRequest
    savePendingExpenseRequest: ...,
  } satisfies SaveRequest;

  try {
    const data = await api.save(body);
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
| **id** | `number` |  | [Defaults to `undefined`] |
| **savePendingExpenseRequest** | [SavePendingExpenseRequest](SavePendingExpenseRequest.md) |  | |

### Return type

[**ExpenseResponse**](ExpenseResponse.md)

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


## saveAsIncome

> IncomeResponse saveAsIncome(id, savePendingIncomeRequest)



### Example

```ts
import {
  Configuration,
  PendingExpenseControllerApi,
} from '';
import type { SaveAsIncomeRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PendingExpenseControllerApi();

  const body = {
    // number
    id: 789,
    // SavePendingIncomeRequest
    savePendingIncomeRequest: ...,
  } satisfies SaveAsIncomeRequest;

  try {
    const data = await api.saveAsIncome(body);
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
| **id** | `number` |  | [Defaults to `undefined`] |
| **savePendingIncomeRequest** | [SavePendingIncomeRequest](SavePendingIncomeRequest.md) |  | |

### Return type

[**IncomeResponse**](IncomeResponse.md)

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


## subscribe

> SseEmitter subscribe()



### Example

```ts
import {
  Configuration,
  PendingExpenseControllerApi,
} from '';
import type { SubscribeRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PendingExpenseControllerApi();

  try {
    const data = await api.subscribe();
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
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

