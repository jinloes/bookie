# IncomeControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                  | HTTP request                              | Description |
| ----------------------------------------------------------------------- | ----------------------------------------- | ----------- |
| [**acceptPendingIncome**](IncomeControllerApi.md#acceptpendingincome)   | **POST** /api/incomes/pending/{id}/accept |             |
| [**createIncome**](IncomeControllerApi.md#createincomeoperation)        | **POST** /api/incomes                     |             |
| [**deleteIncome**](IncomeControllerApi.md#deleteincome)                 | **DELETE** /api/incomes/{id}              |             |
| [**getIncomeById**](IncomeControllerApi.md#getincomebyid)               | **GET** /api/incomes/{id}                 |             |
| [**getIncomes**](IncomeControllerApi.md#getincomes)                     | **GET** /api/incomes                      |             |
| [**getIncomesTotal**](IncomeControllerApi.md#getincomestotal)           | **GET** /api/incomes/total                |             |
| [**getPendingIncomeById**](IncomeControllerApi.md#getpendingincomebyid) | **GET** /api/incomes/pending/{id}         |             |
| [**getPendingIncomes**](IncomeControllerApi.md#getpendingincomes)       | **GET** /api/incomes/pending              |             |
| [**importVenmoIncomeCsv**](IncomeControllerApi.md#importvenmoincomecsv) | **POST** /api/incomes/import/venmo        |             |
| [**rejectPendingIncome**](IncomeControllerApi.md#rejectpendingincome)   | **DELETE** /api/incomes/pending/{id}      |             |
| [**updateIncome**](IncomeControllerApi.md#updateincomeoperation)        | **PUT** /api/incomes/{id}                 |             |

## acceptPendingIncome

> IncomeResponse acceptPendingIncome(id, updateIncomeRequest)

### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { AcceptPendingIncomeRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
    // UpdateIncomeRequest
    updateIncomeRequest: ...,
  } satisfies AcceptPendingIncomeRequest;

  try {
    const data = await api.acceptPendingIncome(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                    | Type                                          | Description | Notes                     |
| ----------------------- | --------------------------------------------- | ----------- | ------------------------- |
| **id**                  | `number`                                      |             | [Defaults to `undefined`] |
| **updateIncomeRequest** | [UpdateIncomeRequest](UpdateIncomeRequest.md) |             |                           |

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

## createIncome

> IncomeResponse createIncome(createIncomeRequest)

### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { CreateIncomeOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  const body = {
    // CreateIncomeRequest
    createIncomeRequest: ...,
  } satisfies CreateIncomeOperationRequest;

  try {
    const data = await api.createIncome(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                    | Type                                          | Description | Notes |
| ----------------------- | --------------------------------------------- | ----------- | ----- |
| **createIncomeRequest** | [CreateIncomeRequest](CreateIncomeRequest.md) |             |       |

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

## deleteIncome

> deleteIncome(id)

### Example

```ts
import { Configuration, IncomeControllerApi } from '';
import type { DeleteIncomeRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies DeleteIncomeRequest;

  try {
    const data = await api.deleteIncome(body);
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

## getIncomeById

> IncomeResponse getIncomeById(id)

### Example

```ts
import { Configuration, IncomeControllerApi } from '';
import type { GetIncomeByIdRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetIncomeByIdRequest;

  try {
    const data = await api.getIncomeById(body);
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

[**IncomeResponse**](IncomeResponse.md)

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

## getIncomes

> Array&lt;IncomeResponse&gt; getIncomes()

### Example

```ts
import { Configuration, IncomeControllerApi } from '';
import type { GetIncomesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new IncomeControllerApi();

  try {
    const data = await api.getIncomes();
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

[**Array&lt;IncomeResponse&gt;**](IncomeResponse.md)

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

## getIncomesTotal

> TotalAmountResponse getIncomesTotal()

### Example

```ts
import { Configuration, IncomeControllerApi } from '';
import type { GetIncomesTotalRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new IncomeControllerApi();

  try {
    const data = await api.getIncomesTotal();
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

## getPendingIncomeById

> PendingIncomeResponse getPendingIncomeById(id)

### Example

```ts
import { Configuration, IncomeControllerApi } from '';
import type { GetPendingIncomeByIdRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetPendingIncomeByIdRequest;

  try {
    const data = await api.getPendingIncomeById(body);
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

[**PendingIncomeResponse**](PendingIncomeResponse.md)

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

## getPendingIncomes

> Array&lt;PendingIncomeResponse&gt; getPendingIncomes()

### Example

```ts
import { Configuration, IncomeControllerApi } from '';
import type { GetPendingIncomesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new IncomeControllerApi();

  try {
    const data = await api.getPendingIncomes();
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

[**Array&lt;PendingIncomeResponse&gt;**](PendingIncomeResponse.md)

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

## importVenmoIncomeCsv

> VenmoIncomeImportResponse importVenmoIncomeCsv(payer, payerId, senderName, propertyId, uploadReceiptRequest)

### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { ImportVenmoIncomeCsvRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  const body = {
    // string (optional)
    payer: payer_example,
    // string (optional)
    payerId: payerId_example,
    // string (optional)
    senderName: senderName_example,
    // string (optional)
    propertyId: propertyId_example,
    // UploadReceiptRequest (optional)
    uploadReceiptRequest: ...,
  } satisfies ImportVenmoIncomeCsvRequest;

  try {
    const data = await api.importVenmoIncomeCsv(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                     | Type                                            | Description | Notes                                |
| ------------------------ | ----------------------------------------------- | ----------- | ------------------------------------ |
| **payer**                | `string`                                        |             | [Optional] [Defaults to `undefined`] |
| **payerId**              | `string`                                        |             | [Optional] [Defaults to `undefined`] |
| **senderName**           | `string`                                        |             | [Optional] [Defaults to `undefined`] |
| **propertyId**           | `string`                                        |             | [Optional] [Defaults to `undefined`] |
| **uploadReceiptRequest** | [UploadReceiptRequest](UploadReceiptRequest.md) |             | [Optional]                           |

### Return type

[**VenmoIncomeImportResponse**](VenmoIncomeImportResponse.md)

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

## rejectPendingIncome

> rejectPendingIncome(id)

### Example

```ts
import { Configuration, IncomeControllerApi } from '';
import type { RejectPendingIncomeRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies RejectPendingIncomeRequest;

  try {
    const data = await api.rejectPendingIncome(body);
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

## updateIncome

> IncomeResponse updateIncome(id, updateIncomeRequest)

### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { UpdateIncomeOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
    // UpdateIncomeRequest
    updateIncomeRequest: ...,
  } satisfies UpdateIncomeOperationRequest;

  try {
    const data = await api.updateIncome(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                    | Type                                          | Description | Notes                     |
| ----------------------- | --------------------------------------------- | ----------- | ------------------------- |
| **id**                  | `number`                                      |             | [Defaults to `undefined`] |
| **updateIncomeRequest** | [UpdateIncomeRequest](UpdateIncomeRequest.md) |             |                           |

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
