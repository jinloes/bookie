# PayerControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                         | HTTP request                 | Description |
| -------------------------------------------------------------- | ---------------------------- | ----------- |
| [**createPayer**](PayerControllerApi.md#createpayer)           | **POST** /api/payers         |             |
| [**deletePayer**](PayerControllerApi.md#deletepayer)           | **DELETE** /api/payers/{id}  |             |
| [**getPayerById**](PayerControllerApi.md#getpayerbyid)         | **GET** /api/payers/{id}     |             |
| [**getPayerKeywords**](PayerControllerApi.md#getpayerkeywords) | **GET** /api/payers/keywords |             |
| [**getPayerTypes**](PayerControllerApi.md#getpayertypes)       | **GET** /api/payers/types    |             |
| [**getPayers**](PayerControllerApi.md#getpayers)               | **GET** /api/payers          |             |
| [**updatePayer**](PayerControllerApi.md#updatepayer)           | **PUT** /api/payers/{id}     |             |

## createPayer

> PayerResponse createPayer(upsertPayerRequest)

### Example

```ts
import {
  Configuration,
  PayerControllerApi,
} from '';
import type { CreatePayerRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PayerControllerApi();

  const body = {
    // UpsertPayerRequest
    upsertPayerRequest: ...,
  } satisfies CreatePayerRequest;

  try {
    const data = await api.createPayer(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                   | Type                                        | Description | Notes |
| ---------------------- | ------------------------------------------- | ----------- | ----- |
| **upsertPayerRequest** | [UpsertPayerRequest](UpsertPayerRequest.md) |             |       |

### Return type

[**PayerResponse**](PayerResponse.md)

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

## deletePayer

> deletePayer(id)

### Example

```ts
import { Configuration, PayerControllerApi } from '';
import type { DeletePayerRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PayerControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies DeletePayerRequest;

  try {
    const data = await api.deletePayer(body);
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

## getPayerById

> PayerResponse getPayerById(id)

### Example

```ts
import { Configuration, PayerControllerApi } from '';
import type { GetPayerByIdRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PayerControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetPayerByIdRequest;

  try {
    const data = await api.getPayerById(body);
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

[**PayerResponse**](PayerResponse.md)

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

## getPayerKeywords

> Array&lt;EmailKeywordPayerHistory&gt; getPayerKeywords()

### Example

```ts
import { Configuration, PayerControllerApi } from '';
import type { GetPayerKeywordsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PayerControllerApi();

  try {
    const data = await api.getPayerKeywords();
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

[**Array&lt;EmailKeywordPayerHistory&gt;**](EmailKeywordPayerHistory.md)

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

## getPayerTypes

> Array&lt;EnumOptionResponse&gt; getPayerTypes()

### Example

```ts
import { Configuration, PayerControllerApi } from '';
import type { GetPayerTypesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PayerControllerApi();

  try {
    const data = await api.getPayerTypes();
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

[**Array&lt;EnumOptionResponse&gt;**](EnumOptionResponse.md)

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

## getPayers

> Array&lt;PayerResponse&gt; getPayers()

### Example

```ts
import { Configuration, PayerControllerApi } from '';
import type { GetPayersRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PayerControllerApi();

  try {
    const data = await api.getPayers();
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

[**Array&lt;PayerResponse&gt;**](PayerResponse.md)

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

## updatePayer

> PayerResponse updatePayer(id, upsertPayerRequest)

### Example

```ts
import {
  Configuration,
  PayerControllerApi,
} from '';
import type { UpdatePayerRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PayerControllerApi();

  const body = {
    // number
    id: 789,
    // UpsertPayerRequest
    upsertPayerRequest: ...,
  } satisfies UpdatePayerRequest;

  try {
    const data = await api.updatePayer(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                   | Type                                        | Description | Notes                     |
| ---------------------- | ------------------------------------------- | ----------- | ------------------------- |
| **id**                 | `number`                                    |             | [Defaults to `undefined`] |
| **upsertPayerRequest** | [UpsertPayerRequest](UpsertPayerRequest.md) |             |                           |

### Return type

[**PayerResponse**](PayerResponse.md)

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
