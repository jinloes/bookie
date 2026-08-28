# TransactionControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                          | HTTP request                         | Description |
| ------------------------------------------------------------------------------- | ------------------------------------ | ----------- |
| [**createTransaction**](TransactionControllerApi.md#createtransactionoperation) | **POST** /api/v2/transactions        |             |
| [**deleteTransaction**](TransactionControllerApi.md#deletetransaction)          | **DELETE** /api/v2/transactions/{id} |             |
| [**getTransactionById**](TransactionControllerApi.md#gettransactionbyid)        | **GET** /api/v2/transactions/{id}    |             |
| [**getTransactions**](TransactionControllerApi.md#gettransactions)              | **GET** /api/v2/transactions         |             |
| [**updateTransaction**](TransactionControllerApi.md#updatetransactionoperation) | **PUT** /api/v2/transactions/{id}    |             |

## createTransaction

> TransactionResponse createTransaction(createTransactionRequest)

### Example

```ts
import {
  Configuration,
  TransactionControllerApi,
} from '';
import type { CreateTransactionOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new TransactionControllerApi();

  const body = {
    // CreateTransactionRequest
    createTransactionRequest: ...,
  } satisfies CreateTransactionOperationRequest;

  try {
    const data = await api.createTransaction(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                         | Type                                                    | Description | Notes |
| ---------------------------- | ------------------------------------------------------- | ----------- | ----- |
| **createTransactionRequest** | [CreateTransactionRequest](CreateTransactionRequest.md) |             |       |

### Return type

[**TransactionResponse**](TransactionResponse.md)

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

## deleteTransaction

> deleteTransaction(id, version)

### Example

```ts
import { Configuration, TransactionControllerApi } from '';
import type { DeleteTransactionRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new TransactionControllerApi();

  const body = {
    // number
    id: 789,
    // number
    version: 789,
  } satisfies DeleteTransactionRequest;

  try {
    const data = await api.deleteTransaction(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name        | Type     | Description | Notes                     |
| ----------- | -------- | ----------- | ------------------------- |
| **id**      | `number` |             | [Defaults to `undefined`] |
| **version** | `number` |             | [Defaults to `undefined`] |

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

## getTransactionById

> TransactionResponse getTransactionById(id)

### Example

```ts
import { Configuration, TransactionControllerApi } from '';
import type { GetTransactionByIdRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new TransactionControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetTransactionByIdRequest;

  try {
    const data = await api.getTransactionById(body);
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

[**TransactionResponse**](TransactionResponse.md)

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

## getTransactions

> Array&lt;TransactionResponse&gt; getTransactions()

### Example

```ts
import { Configuration, TransactionControllerApi } from '';
import type { GetTransactionsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new TransactionControllerApi();

  try {
    const data = await api.getTransactions();
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

[**Array&lt;TransactionResponse&gt;**](TransactionResponse.md)

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

## updateTransaction

> TransactionResponse updateTransaction(id, updateTransactionRequest)

### Example

```ts
import {
  Configuration,
  TransactionControllerApi,
} from '';
import type { UpdateTransactionOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new TransactionControllerApi();

  const body = {
    // number
    id: 789,
    // UpdateTransactionRequest
    updateTransactionRequest: ...,
  } satisfies UpdateTransactionOperationRequest;

  try {
    const data = await api.updateTransaction(body);
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
| **updateTransactionRequest** | [UpdateTransactionRequest](UpdateTransactionRequest.md) |             |                           |

### Return type

[**TransactionResponse**](TransactionResponse.md)

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
