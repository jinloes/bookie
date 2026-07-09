# PayerControllerApi

All URIs are relative to *http://localhost:48763*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**create1**](PayerControllerApi.md#create1) | **POST** /api/payers |  |
| [**delete1**](PayerControllerApi.md#delete1) | **DELETE** /api/payers/{id} |  |
| [**getAll1**](PayerControllerApi.md#getall1) | **GET** /api/payers |  |
| [**getById1**](PayerControllerApi.md#getbyid1) | **GET** /api/payers/{id} |  |
| [**getKeywords1**](PayerControllerApi.md#getkeywords1) | **GET** /api/payers/keywords |  |
| [**getTypes1**](PayerControllerApi.md#gettypes1) | **GET** /api/payers/types |  |
| [**update1**](PayerControllerApi.md#update1) | **PUT** /api/payers/{id} |  |



## create1

> PayerResponse create1(upsertPayerRequest)



### Example

```ts
import {
  Configuration,
  PayerControllerApi,
} from '';
import type { Create1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PayerControllerApi();

  const body = {
    // UpsertPayerRequest
    upsertPayerRequest: ...,
  } satisfies Create1Request;

  try {
    const data = await api.create1(body);
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
| **upsertPayerRequest** | [UpsertPayerRequest](UpsertPayerRequest.md) |  | |

### Return type

[**PayerResponse**](PayerResponse.md)

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


## delete1

> delete1(id)



### Example

```ts
import {
  Configuration,
  PayerControllerApi,
} from '';
import type { Delete1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PayerControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies Delete1Request;

  try {
    const data = await api.delete1(body);
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
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getAll1

> Array&lt;PayerResponse&gt; getAll1()



### Example

```ts
import {
  Configuration,
  PayerControllerApi,
} from '';
import type { GetAll1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PayerControllerApi();

  try {
    const data = await api.getAll1();
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
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getById1

> PayerResponse getById1(id)



### Example

```ts
import {
  Configuration,
  PayerControllerApi,
} from '';
import type { GetById1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PayerControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetById1Request;

  try {
    const data = await api.getById1(body);
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

[**PayerResponse**](PayerResponse.md)

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


## getKeywords1

> Array&lt;EmailKeywordPayerHistory&gt; getKeywords1()



### Example

```ts
import {
  Configuration,
  PayerControllerApi,
} from '';
import type { GetKeywords1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PayerControllerApi();

  try {
    const data = await api.getKeywords1();
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
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getTypes1

> Array&lt;EnumOptionResponse&gt; getTypes1()



### Example

```ts
import {
  Configuration,
  PayerControllerApi,
} from '';
import type { GetTypes1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PayerControllerApi();

  try {
    const data = await api.getTypes1();
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
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## update1

> PayerResponse update1(id, upsertPayerRequest)



### Example

```ts
import {
  Configuration,
  PayerControllerApi,
} from '';
import type { Update1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new PayerControllerApi();

  const body = {
    // number
    id: 789,
    // UpsertPayerRequest
    upsertPayerRequest: ...,
  } satisfies Update1Request;

  try {
    const data = await api.update1(body);
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
| **upsertPayerRequest** | [UpsertPayerRequest](UpsertPayerRequest.md) |  | |

### Return type

[**PayerResponse**](PayerResponse.md)

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

