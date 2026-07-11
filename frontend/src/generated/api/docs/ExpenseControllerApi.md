# ExpenseControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                   | HTTP request                     | Description |
| ------------------------------------------------------------------------ | -------------------------------- | ----------- |
| [**createExpense**](ExpenseControllerApi.md#createexpenseoperation)      | **POST** /api/expenses           |             |
| [**deleteExpense**](ExpenseControllerApi.md#deleteexpense)               | **DELETE** /api/expenses/{id}    |             |
| [**getExpenseById**](ExpenseControllerApi.md#getexpensebyid)             | **GET** /api/expenses/{id}       |             |
| [**getExpenseCategories**](ExpenseControllerApi.md#getexpensecategories) | **GET** /api/expenses/categories |             |
| [**getExpenses**](ExpenseControllerApi.md#getexpenses)                   | **GET** /api/expenses            |             |
| [**getExpensesTotal**](ExpenseControllerApi.md#getexpensestotal)         | **GET** /api/expenses/total      |             |
| [**updateExpense**](ExpenseControllerApi.md#updateexpenseoperation)      | **PUT** /api/expenses/{id}       |             |

## createExpense

> ExpenseResponse createExpense(createExpenseRequest)

### Example

```ts
import {
  Configuration,
  ExpenseControllerApi,
} from '';
import type { CreateExpenseOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ExpenseControllerApi();

  const body = {
    // CreateExpenseRequest
    createExpenseRequest: ...,
  } satisfies CreateExpenseOperationRequest;

  try {
    const data = await api.createExpense(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                     | Type                                            | Description | Notes |
| ------------------------ | ----------------------------------------------- | ----------- | ----- |
| **createExpenseRequest** | [CreateExpenseRequest](CreateExpenseRequest.md) |             |       |

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

## deleteExpense

> deleteExpense(id)

### Example

```ts
import { Configuration, ExpenseControllerApi } from '';
import type { DeleteExpenseRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ExpenseControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies DeleteExpenseRequest;

  try {
    const data = await api.deleteExpense(body);
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
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getExpenseById

> ExpenseResponse getExpenseById(id)

### Example

```ts
import { Configuration, ExpenseControllerApi } from '';
import type { GetExpenseByIdRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ExpenseControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetExpenseByIdRequest;

  try {
    const data = await api.getExpenseById(body);
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

[**ExpenseResponse**](ExpenseResponse.md)

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

## getExpenseCategories

> Array&lt;ExpenseCategoryDto&gt; getExpenseCategories()

### Example

```ts
import { Configuration, ExpenseControllerApi } from '';
import type { GetExpenseCategoriesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ExpenseControllerApi();

  try {
    const data = await api.getExpenseCategories();
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

[**Array&lt;ExpenseCategoryDto&gt;**](ExpenseCategoryDto.md)

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

## getExpenses

> Array&lt;ExpenseResponse&gt; getExpenses()

### Example

```ts
import { Configuration, ExpenseControllerApi } from '';
import type { GetExpensesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ExpenseControllerApi();

  try {
    const data = await api.getExpenses();
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

[**Array&lt;ExpenseResponse&gt;**](ExpenseResponse.md)

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

## getExpensesTotal

> TotalAmountResponse getExpensesTotal()

### Example

```ts
import { Configuration, ExpenseControllerApi } from '';
import type { GetExpensesTotalRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new ExpenseControllerApi();

  try {
    const data = await api.getExpensesTotal();
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

[**TotalAmountResponse**](TotalAmountResponse.md)

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

## updateExpense

> ExpenseResponse updateExpense(id, updateExpenseRequest)

### Example

```ts
import {
  Configuration,
  ExpenseControllerApi,
} from '';
import type { UpdateExpenseOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ExpenseControllerApi();

  const body = {
    // number
    id: 789,
    // UpdateExpenseRequest
    updateExpenseRequest: ...,
  } satisfies UpdateExpenseOperationRequest;

  try {
    const data = await api.updateExpense(body);
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
| **updateExpenseRequest** | [UpdateExpenseRequest](UpdateExpenseRequest.md) |             |                           |

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
