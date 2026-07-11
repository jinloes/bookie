# PropertyControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                  | HTTP request                     | Description |
| ----------------------------------------------------------------------- | -------------------------------- | ----------- |
| [**createProperty**](PropertyControllerApi.md#createpropertyoperation)  | **POST** /api/properties         |             |
| [**deleteProperty**](PropertyControllerApi.md#deleteproperty)           | **DELETE** /api/properties/{id}  |             |
| [**getProperties**](PropertyControllerApi.md#getproperties)             | **GET** /api/properties          |             |
| [**getPropertyById**](PropertyControllerApi.md#getpropertybyid)         | **GET** /api/properties/{id}     |             |
| [**getPropertyKeywords**](PropertyControllerApi.md#getpropertykeywords) | **GET** /api/properties/keywords |             |
| [**getPropertyTypes**](PropertyControllerApi.md#getpropertytypes)       | **GET** /api/properties/types    |             |
| [**updateProperty**](PropertyControllerApi.md#updatepropertyoperation)  | **PUT** /api/properties/{id}     |             |

## createProperty

> PropertyResponse createProperty(createPropertyRequest)

### Example

```ts
import {
  Configuration,
  PropertyControllerApi,
} from '';
import type { CreatePropertyOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PropertyControllerApi();

  const body = {
    // CreatePropertyRequest
    createPropertyRequest: ...,
  } satisfies CreatePropertyOperationRequest;

  try {
    const data = await api.createProperty(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                      | Type                                              | Description | Notes |
| ------------------------- | ------------------------------------------------- | ----------- | ----- |
| **createPropertyRequest** | [CreatePropertyRequest](CreatePropertyRequest.md) |             |       |

### Return type

[**PropertyResponse**](PropertyResponse.md)

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

## deleteProperty

> deleteProperty(id)

### Example

```ts
import { Configuration, PropertyControllerApi } from '';
import type { DeletePropertyRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PropertyControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies DeletePropertyRequest;

  try {
    const data = await api.deleteProperty(body);
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

## getProperties

> Array&lt;PropertyResponse&gt; getProperties()

### Example

```ts
import { Configuration, PropertyControllerApi } from '';
import type { GetPropertiesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PropertyControllerApi();

  try {
    const data = await api.getProperties();
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

[**Array&lt;PropertyResponse&gt;**](PropertyResponse.md)

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

## getPropertyById

> PropertyResponse getPropertyById(id)

### Example

```ts
import { Configuration, PropertyControllerApi } from '';
import type { GetPropertyByIdRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PropertyControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetPropertyByIdRequest;

  try {
    const data = await api.getPropertyById(body);
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

[**PropertyResponse**](PropertyResponse.md)

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

## getPropertyKeywords

> Array&lt;EmailKeywordPropertyHistory&gt; getPropertyKeywords()

### Example

```ts
import { Configuration, PropertyControllerApi } from '';
import type { GetPropertyKeywordsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PropertyControllerApi();

  try {
    const data = await api.getPropertyKeywords();
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

[**Array&lt;EmailKeywordPropertyHistory&gt;**](EmailKeywordPropertyHistory.md)

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

## getPropertyTypes

> Array&lt;EnumOptionResponse&gt; getPropertyTypes()

### Example

```ts
import { Configuration, PropertyControllerApi } from '';
import type { GetPropertyTypesRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new PropertyControllerApi();

  try {
    const data = await api.getPropertyTypes();
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

## updateProperty

> PropertyResponse updateProperty(id, updatePropertyRequest)

### Example

```ts
import {
  Configuration,
  PropertyControllerApi,
} from '';
import type { UpdatePropertyOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PropertyControllerApi();

  const body = {
    // number
    id: 789,
    // UpdatePropertyRequest
    updatePropertyRequest: ...,
  } satisfies UpdatePropertyOperationRequest;

  try {
    const data = await api.updateProperty(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                      | Type                                              | Description | Notes                     |
| ------------------------- | ------------------------------------------------- | ----------- | ------------------------- |
| **id**                    | `number`                                          |             | [Defaults to `undefined`] |
| **updatePropertyRequest** | [UpdatePropertyRequest](UpdatePropertyRequest.md) |             |                           |

### Return type

[**PropertyResponse**](PropertyResponse.md)

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
