# PendingExpenseControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                                                | HTTP request                                    | Description |
| ----------------------------------------------------------------------------------------------------- | ----------------------------------------------- | ----------- |
| [**createExpenseFromPendingExpense**](PendingExpenseControllerApi.md#createexpensefrompendingexpense) | **POST** /api/pending-expenses/{id}/save        |             |
| [**createIncomeFromPendingExpense**](PendingExpenseControllerApi.md#createincomefrompendingexpense)   | **POST** /api/pending-expenses/{id}/save-income |             |
| [**dismissPendingExpense**](PendingExpenseControllerApi.md#dismisspendingexpense)                     | **DELETE** /api/pending-expenses/{id}           |             |
| [**getPendingExpenses**](PendingExpenseControllerApi.md#getpendingexpenses)                           | **GET** /api/pending-expenses                   |             |
| [**retryPendingExpense**](PendingExpenseControllerApi.md#retrypendingexpense)                         | **POST** /api/pending-expenses/{id}/retry       |             |
| [**subscribePendingExpenseEvents**](PendingExpenseControllerApi.md#subscribependingexpenseevents)     | **GET** /api/pending-expenses/events            |             |

## createExpenseFromPendingExpense

> ExpenseResponse createExpenseFromPendingExpense(id, savePendingExpenseRequest)

### Example

```ts
import {
  Configuration,
  PendingExpenseControllerApi,
} from '';
import type { CreateExpenseFromPendingExpenseRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PendingExpenseControllerApi();

  const body = {
    // number
    id: 789,
    // SavePendingExpenseRequest
    savePendingExpenseRequest: ...,
  } satisfies CreateExpenseFromPendingExpenseRequest;

  try {
    const data = await api.createExpenseFromPendingExpense(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                          | Type                                                      | Description | Notes                     |
| ----------------------------- | --------------------------------------------------------- | ----------- | ------------------------- |
| **id**                        | `number`                                                  |             | [Defaults to `undefined`] |
| **savePendingExpenseRequest** | [SavePendingExpenseRequest](SavePendingExpenseRequest.md) |             |                           |

### Return type

[**ExpenseResponse**](ExpenseResponse.md)

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

## createIncomeFromPendingExpense

> IncomeResponse createIncomeFromPendingExpense(id, savePendingIncomeRequest)

### Example

```ts
import {
  Configuration,
  PendingExpenseControllerApi,
} from '';
import type { CreateIncomeFromPendingExpenseRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PendingExpenseControllerApi();

  const body = {
    // number
    id: 789,
    // SavePendingIncomeRequest
    savePendingIncomeRequest: ...,
  } satisfies CreateIncomeFromPendingExpenseRequest;

  try {
    const data = await api.createIncomeFromPendingExpense(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                         | Type                                                    | Description | Notes                     |
| ---------------------------- | ------------------------------------------------------- | ----------- | ------------------------- |
| **id**                       | `number`                                                |             | [Defaults to `undefined`] |
| **savePendingIncomeRequest** | [SavePendingIncomeRequest](SavePendingIncomeRequest.md) |             |                           |

### Return type

[**IncomeResponse**](IncomeResponse.md)

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

## dismissPendingExpense

> dismissPendingExpense(id)

### Example

```ts
import { Configuration, PendingExpenseControllerApi } from '';
import type { DismissPendingExpenseRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PendingExpenseControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies DismissPendingExpenseRequest;

  try {
    const data = await api.dismissPendingExpense(body);
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
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **204**     | No Content            | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getPendingExpenses

> Array&lt;PendingExpenseResponse&gt; getPendingExpenses()

### Example

```ts
import { Configuration, PendingExpenseControllerApi } from '';
import type { GetPendingExpensesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PendingExpenseControllerApi();

  try {
    const data = await api.getPendingExpenses();
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

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## retryPendingExpense

> retryPendingExpense(id)

### Example

```ts
import { Configuration, PendingExpenseControllerApi } from '';
import type { RetryPendingExpenseRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PendingExpenseControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies RetryPendingExpenseRequest;

  try {
    const data = await api.retryPendingExpense(body);
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
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **202**     | Accepted              | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## subscribePendingExpenseEvents

> SseEmitter subscribePendingExpenseEvents()

### Example

```ts
import { Configuration, PendingExpenseControllerApi } from '';
import type { SubscribePendingExpenseEventsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PendingExpenseControllerApi();

  try {
    const data = await api.subscribePendingExpenseEvents();
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

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
