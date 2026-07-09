# ExpenseControllerApi

All URIs are relative to *http://localhost:48763*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**create3**](ExpenseControllerApi.md#create3) | **POST** /api/expenses |  |
| [**delete3**](ExpenseControllerApi.md#delete3) | **DELETE** /api/expenses/{id} |  |
| [**getAll3**](ExpenseControllerApi.md#getall3) | **GET** /api/expenses |  |
| [**getById3**](ExpenseControllerApi.md#getbyid3) | **GET** /api/expenses/{id} |  |
| [**getCategories**](ExpenseControllerApi.md#getcategories) | **GET** /api/expenses/categories |  |
| [**getTotal1**](ExpenseControllerApi.md#gettotal1) | **GET** /api/expenses/total |  |
| [**update3**](ExpenseControllerApi.md#update3) | **PUT** /api/expenses/{id} |  |



## create3

> ExpenseResponse create3(createExpenseRequest)



### Example

```ts
import {
  Configuration,
  ExpenseControllerApi,
} from '';
import type { Create3Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ExpenseControllerApi();

  const body = {
    // CreateExpenseRequest
    createExpenseRequest: ...,
  } satisfies Create3Request;

  try {
    const data = await api.create3(body);
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
| **createExpenseRequest** | [CreateExpenseRequest](CreateExpenseRequest.md) |  | |

### Return type

[**ExpenseResponse**](ExpenseResponse.md)

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


## delete3

> delete3(id)



### Example

```ts
import {
  Configuration,
  ExpenseControllerApi,
} from '';
import type { Delete3Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ExpenseControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies Delete3Request;

  try {
    const data = await api.delete3(body);
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


## getAll3

> Array&lt;ExpenseResponse&gt; getAll3()



### Example

```ts
import {
  Configuration,
  ExpenseControllerApi,
} from '';
import type { GetAll3Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ExpenseControllerApi();

  try {
    const data = await api.getAll3();
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
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getById3

> ExpenseResponse getById3(id)



### Example

```ts
import {
  Configuration,
  ExpenseControllerApi,
} from '';
import type { GetById3Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ExpenseControllerApi();

  const body = {
    // number
    id: 789,
  } satisfies GetById3Request;

  try {
    const data = await api.getById3(body);
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

[**ExpenseResponse**](ExpenseResponse.md)

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


## getCategories

> Array&lt;ExpenseCategoryDto&gt; getCategories()



### Example

```ts
import {
  Configuration,
  ExpenseControllerApi,
} from '';
import type { GetCategoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ExpenseControllerApi();

  try {
    const data = await api.getCategories();
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
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **400** | Bad Request |  -  |
| **409** | Conflict |  -  |
| **500** | Internal Server Error |  -  |
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getTotal1

> TotalAmountResponse getTotal1()



### Example

```ts
import {
  Configuration,
  ExpenseControllerApi,
} from '';
import type { GetTotal1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ExpenseControllerApi();

  try {
    const data = await api.getTotal1();
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


## update3

> ExpenseResponse update3(id, updateExpenseRequest)



### Example

```ts
import {
  Configuration,
  ExpenseControllerApi,
} from '';
import type { Update3Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new ExpenseControllerApi();

  const body = {
    // number
    id: 789,
    // UpdateExpenseRequest
    updateExpenseRequest: ...,
  } satisfies Update3Request;

  try {
    const data = await api.update3(body);
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
| **updateExpenseRequest** | [UpdateExpenseRequest](UpdateExpenseRequest.md) |  | |

### Return type

[**ExpenseResponse**](ExpenseResponse.md)

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

