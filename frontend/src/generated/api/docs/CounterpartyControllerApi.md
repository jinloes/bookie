# CounterpartyControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                        | HTTP request                        | Description |
| ----------------------------------------------------------------------------- | ----------------------------------- | ----------- |
| [**createCounterparty**](CounterpartyControllerApi.md#createcounterparty)     | **POST** /api/counterparties        |             |
| [**deleteCounterparty**](CounterpartyControllerApi.md#deletecounterparty)     | **DELETE** /api/counterparties/{id} |             |
| [**getCounterparties**](CounterpartyControllerApi.md#getcounterparties)       | **GET** /api/counterparties         |             |
| [**getCounterpartyById**](CounterpartyControllerApi.md#getcounterpartybyid)   | **GET** /api/counterparties/{id}    |             |
| [**getCounterpartyTypes**](CounterpartyControllerApi.md#getcounterpartytypes) | **GET** /api/counterparties/types   |             |
| [**updateCounterparty**](CounterpartyControllerApi.md#updatecounterparty)     | **PUT** /api/counterparties/{id}    |             |

## createCounterparty

> CounterpartyResponse createCounterparty(upsertCounterpartyRequest)

### Example

```ts
import {
  Configuration,
  CounterpartyControllerApi,
} from '';
import type { CreateCounterpartyRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new CounterpartyControllerApi();

  const body = {
    // UpsertCounterpartyRequest
    upsertCounterpartyRequest: ...,
  } satisfies CreateCounterpartyRequest;

  try {
    const data = await api.createCounterparty(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                          | Type                                                      | Description | Notes |
| ----------------------------- | --------------------------------------------------------- | ----------- | ----- |
| **upsertCounterpartyRequest** | [UpsertCounterpartyRequest](UpsertCounterpartyRequest.md) |             |       |

### Return type

[**CounterpartyResponse**](CounterpartyResponse.md)

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

## deleteCounterparty

> deleteCounterparty(id)

### Example

```ts
import { Configuration, CounterpartyControllerApi } from '';
import type { DeleteCounterpartyRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new CounterpartyControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies DeleteCounterpartyRequest;

  try {
    const data = await api.deleteCounterparty(body);
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
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getCounterparties

> Array&lt;CounterpartyResponse&gt; getCounterparties()

### Example

```ts
import { Configuration, CounterpartyControllerApi } from '';
import type { GetCounterpartiesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new CounterpartyControllerApi();

  try {
    const data = await api.getCounterparties();
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

[**Array&lt;CounterpartyResponse&gt;**](CounterpartyResponse.md)

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

## getCounterpartyById

> CounterpartyResponse getCounterpartyById(id)

### Example

```ts
import { Configuration, CounterpartyControllerApi } from '';
import type { GetCounterpartyByIdRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new CounterpartyControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetCounterpartyByIdRequest;

  try {
    const data = await api.getCounterpartyById(body);
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

[**CounterpartyResponse**](CounterpartyResponse.md)

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

## getCounterpartyTypes

> Array&lt;EnumOptionResponse&gt; getCounterpartyTypes()

### Example

```ts
import { Configuration, CounterpartyControllerApi } from '';
import type { GetCounterpartyTypesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new CounterpartyControllerApi();

  try {
    const data = await api.getCounterpartyTypes();
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
| **500**     | Internal Server Error | -                |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## updateCounterparty

> CounterpartyResponse updateCounterparty(id, upsertCounterpartyRequest)

### Example

```ts
import {
  Configuration,
  CounterpartyControllerApi,
} from '';
import type { UpdateCounterpartyRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new CounterpartyControllerApi();

  const body = {
    // number
    id: 789,
    // UpsertCounterpartyRequest
    upsertCounterpartyRequest: ...,
  } satisfies UpdateCounterpartyRequest;

  try {
    const data = await api.updateCounterparty(body);
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
| **upsertCounterpartyRequest** | [UpsertCounterpartyRequest](UpsertCounterpartyRequest.md) |             |                           |

### Return type

[**CounterpartyResponse**](CounterpartyResponse.md)

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
