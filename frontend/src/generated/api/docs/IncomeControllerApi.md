# IncomeControllerApi

All URIs are relative to *http://localhost:48763*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**acceptPending**](IncomeControllerApi.md#acceptpending) | **POST** /api/incomes/pending/{id}/accept |  |
| [**create2**](IncomeControllerApi.md#create2) | **POST** /api/incomes |  |
| [**delete2**](IncomeControllerApi.md#delete2) | **DELETE** /api/incomes/{id} |  |
| [**getAll2**](IncomeControllerApi.md#getall2) | **GET** /api/incomes |  |
| [**getById2**](IncomeControllerApi.md#getbyid2) | **GET** /api/incomes/{id} |  |
| [**getPending**](IncomeControllerApi.md#getpending) | **GET** /api/incomes/pending |  |
| [**getPendingById**](IncomeControllerApi.md#getpendingbyid) | **GET** /api/incomes/pending/{id} |  |
| [**getTotal**](IncomeControllerApi.md#gettotal) | **GET** /api/incomes/total |  |
| [**importVenmoCsv**](IncomeControllerApi.md#importvenmocsv) | **POST** /api/incomes/import/venmo |  |
| [**rejectPending**](IncomeControllerApi.md#rejectpending) | **DELETE** /api/incomes/pending/{id} |  |
| [**update2**](IncomeControllerApi.md#update2) | **PUT** /api/incomes/{id} |  |



## acceptPending

> IncomeResponse acceptPending(id, updateIncomeRequest)



### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { AcceptPendingRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
    // UpdateIncomeRequest
    updateIncomeRequest: ...,
  } satisfies AcceptPendingRequest;

  try {
    const data = await api.acceptPending(body);
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
| **updateIncomeRequest** | [UpdateIncomeRequest](UpdateIncomeRequest.md) |  | |

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


## create2

> IncomeResponse create2(createIncomeRequest)



### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { Create2Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  const body = {
    // CreateIncomeRequest
    createIncomeRequest: ...,
  } satisfies Create2Request;

  try {
    const data = await api.create2(body);
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
| **createIncomeRequest** | [CreateIncomeRequest](CreateIncomeRequest.md) |  | |

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


## delete2

> delete2(id)



### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { Delete2Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies Delete2Request;

  try {
    const data = await api.delete2(body);
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


## getAll2

> Array&lt;IncomeResponse&gt; getAll2()



### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { GetAll2Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  try {
    const data = await api.getAll2();
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
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getById2

> IncomeResponse getById2(id)



### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { GetById2Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetById2Request;

  try {
    const data = await api.getById2(body);
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

[**IncomeResponse**](IncomeResponse.md)

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


## getPending

> Array&lt;PendingIncomeResponse&gt; getPending()



### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { GetPendingRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  try {
    const data = await api.getPending();
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
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getPendingById

> PendingIncomeResponse getPendingById(id)



### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { GetPendingByIdRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetPendingByIdRequest;

  try {
    const data = await api.getPendingById(body);
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

[**PendingIncomeResponse**](PendingIncomeResponse.md)

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


## getTotal

> TotalAmountResponse getTotal()



### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { GetTotalRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  try {
    const data = await api.getTotal();
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
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## importVenmoCsv

> VenmoIncomeImportResponse importVenmoCsv(payer, payerId, senderName, propertyId, uploadRequest)



### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { ImportVenmoCsvRequest } from '';

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
    // UploadRequest (optional)
    uploadRequest: ...,
  } satisfies ImportVenmoCsvRequest;

  try {
    const data = await api.importVenmoCsv(body);
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
| **payer** | `string` |  | [Optional] [Defaults to `undefined`] |
| **payerId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **senderName** | `string` |  | [Optional] [Defaults to `undefined`] |
| **propertyId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **uploadRequest** | [UploadRequest](UploadRequest.md) |  | [Optional] |

### Return type

[**VenmoIncomeImportResponse**](VenmoIncomeImportResponse.md)

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


## rejectPending

> rejectPending(id)



### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { RejectPendingRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies RejectPendingRequest;

  try {
    const data = await api.rejectPending(body);
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


## update2

> IncomeResponse update2(id, updateIncomeRequest)



### Example

```ts
import {
  Configuration,
  IncomeControllerApi,
} from '';
import type { Update2Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new IncomeControllerApi();

  const body = {
    // number
    id: 789,
    // UpdateIncomeRequest
    updateIncomeRequest: ...,
  } satisfies Update2Request;

  try {
    const data = await api.update2(body);
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
| **updateIncomeRequest** | [UpdateIncomeRequest](UpdateIncomeRequest.md) |  | |

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

