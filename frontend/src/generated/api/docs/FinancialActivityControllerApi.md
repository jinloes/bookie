# FinancialActivityControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                                       | HTTP request                           | Description |
| -------------------------------------------------------------------------------------------- | -------------------------------------- | ----------- |
| [**createFinancialActivity**](FinancialActivityControllerApi.md#createfinancialactivity)     | **POST** /api/activities               |             |
| [**getFinancialActivities**](FinancialActivityControllerApi.md#getfinancialactivities)       | **GET** /api/activities                |             |
| [**getFinancialActivityTypes**](FinancialActivityControllerApi.md#getfinancialactivitytypes) | **GET** /api/activities/types          |             |
| [**getTaxTreatments**](FinancialActivityControllerApi.md#gettaxtreatments)                   | **GET** /api/activities/tax-treatments |             |
| [**updateFinancialActivity**](FinancialActivityControllerApi.md#updatefinancialactivity)     | **PUT** /api/activities/{id}           |             |

## createFinancialActivity

> FinancialActivityResponse createFinancialActivity(upsertFinancialActivityRequest)

### Example

```ts
import {
  Configuration,
  FinancialActivityControllerApi,
} from '';
import type { CreateFinancialActivityRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new FinancialActivityControllerApi();

  const body = {
    // UpsertFinancialActivityRequest
    upsertFinancialActivityRequest: ...,
  } satisfies CreateFinancialActivityRequest;

  try {
    const data = await api.createFinancialActivity(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                               | Type                                                                | Description | Notes |
| ---------------------------------- | ------------------------------------------------------------------- | ----------- | ----- |
| **upsertFinancialActivityRequest** | [UpsertFinancialActivityRequest](UpsertFinancialActivityRequest.md) |             |       |

### Return type

[**FinancialActivityResponse**](FinancialActivityResponse.md)

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

## getFinancialActivities

> Array&lt;FinancialActivityResponse&gt; getFinancialActivities()

### Example

```ts
import { Configuration, FinancialActivityControllerApi } from '';
import type { GetFinancialActivitiesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new FinancialActivityControllerApi();

  try {
    const data = await api.getFinancialActivities();
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

[**Array&lt;FinancialActivityResponse&gt;**](FinancialActivityResponse.md)

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

## getFinancialActivityTypes

> Array&lt;EnumOptionResponse&gt; getFinancialActivityTypes()

### Example

```ts
import { Configuration, FinancialActivityControllerApi } from '';
import type { GetFinancialActivityTypesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new FinancialActivityControllerApi();

  try {
    const data = await api.getFinancialActivityTypes();
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

## getTaxTreatments

> Array&lt;EnumOptionResponse&gt; getTaxTreatments()

### Example

```ts
import { Configuration, FinancialActivityControllerApi } from '';
import type { GetTaxTreatmentsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new FinancialActivityControllerApi();

  try {
    const data = await api.getTaxTreatments();
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

## updateFinancialActivity

> FinancialActivityResponse updateFinancialActivity(id, upsertFinancialActivityRequest)

### Example

```ts
import {
  Configuration,
  FinancialActivityControllerApi,
} from '';
import type { UpdateFinancialActivityRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new FinancialActivityControllerApi();

  const body = {
    // number
    id: 789,
    // UpsertFinancialActivityRequest
    upsertFinancialActivityRequest: ...,
  } satisfies UpdateFinancialActivityRequest;

  try {
    const data = await api.updateFinancialActivity(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                               | Type                                                                | Description | Notes                     |
| ---------------------------------- | ------------------------------------------------------------------- | ----------- | ------------------------- |
| **id**                             | `number`                                                            |             | [Defaults to `undefined`] |
| **upsertFinancialActivityRequest** | [UpsertFinancialActivityRequest](UpsertFinancialActivityRequest.md) |             |                           |

### Return type

[**FinancialActivityResponse**](FinancialActivityResponse.md)

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
