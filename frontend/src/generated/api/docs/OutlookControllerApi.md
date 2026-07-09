# OutlookControllerApi

All URIs are relative to *http://localhost:48763*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**callback**](OutlookControllerApi.md#callback) | **GET** /api/outlook/callback |  |
| [**connect**](OutlookControllerApi.md#connect) | **GET** /api/outlook/connect |  |
| [**getAvailableFolders**](OutlookControllerApi.md#getavailablefolders) | **GET** /api/outlook/folders/available |  |
| [**getConfiguredFolderSettings**](OutlookControllerApi.md#getconfiguredfoldersettings) | **GET** /api/outlook/settings/folders |  |
| [**getEmailContent**](OutlookControllerApi.md#getemailcontent) | **GET** /api/outlook/emails/{messageId}/content |  |
| [**getMoveSettings**](OutlookControllerApi.md#getmovesettings) | **GET** /api/outlook/settings/move |  |
| [**getRentalEmails**](OutlookControllerApi.md#getrentalemails) | **GET** /api/outlook/emails/rental |  |
| [**parseEmail**](OutlookControllerApi.md#parseemail) | **POST** /api/outlook/emails/{messageId}/parse |  |
| [**status**](OutlookControllerApi.md#status) | **GET** /api/outlook/status |  |
| [**updateConfiguredFolderSettings**](OutlookControllerApi.md#updateconfiguredfoldersettings) | **PUT** /api/outlook/settings/folders |  |
| [**updateMoveSettings**](OutlookControllerApi.md#updatemovesettings) | **PUT** /api/outlook/settings/move |  |



## callback

> RedirectView callback(code, state, error, errorDescription)



### Example

```ts
import {
  Configuration,
  OutlookControllerApi,
} from '';
import type { CallbackRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new OutlookControllerApi();

  const body = {
    // string (optional)
    code: code_example,
    // string (optional)
    state: state_example,
    // string (optional)
    error: error_example,
    // string (optional)
    errorDescription: errorDescription_example,
  } satisfies CallbackRequest;

  try {
    const data = await api.callback(body);
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
| **code** | `string` |  | [Optional] [Defaults to `undefined`] |
| **state** | `string` |  | [Optional] [Defaults to `undefined`] |
| **error** | `string` |  | [Optional] [Defaults to `undefined`] |
| **errorDescription** | `string` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**RedirectView**](RedirectView.md)

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


## connect

> RedirectView connect()



### Example

```ts
import {
  Configuration,
  OutlookControllerApi,
} from '';
import type { ConnectRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new OutlookControllerApi();

  try {
    const data = await api.connect();
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

[**RedirectView**](RedirectView.md)

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


## getAvailableFolders

> Array&lt;FolderInfo&gt; getAvailableFolders()



### Example

```ts
import {
  Configuration,
  OutlookControllerApi,
} from '';
import type { GetAvailableFoldersRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new OutlookControllerApi();

  try {
    const data = await api.getAvailableFolders();
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

[**Array&lt;FolderInfo&gt;**](FolderInfo.md)

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


## getConfiguredFolderSettings

> Array&lt;FolderSetting&gt; getConfiguredFolderSettings()



### Example

```ts
import {
  Configuration,
  OutlookControllerApi,
} from '';
import type { GetConfiguredFolderSettingsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new OutlookControllerApi();

  try {
    const data = await api.getConfiguredFolderSettings();
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

[**Array&lt;FolderSetting&gt;**](FolderSetting.md)

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


## getEmailContent

> MessageContent getEmailContent(messageId)



### Example

```ts
import {
  Configuration,
  OutlookControllerApi,
} from '';
import type { GetEmailContentRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new OutlookControllerApi();

  const body = {
    // string
    messageId: messageId_example,
  } satisfies GetEmailContentRequest;

  try {
    const data = await api.getEmailContent(body);
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
| **messageId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**MessageContent**](MessageContent.md)

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


## getMoveSettings

> MoveSettings getMoveSettings()



### Example

```ts
import {
  Configuration,
  OutlookControllerApi,
} from '';
import type { GetMoveSettingsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new OutlookControllerApi();

  try {
    const data = await api.getMoveSettings();
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

[**MoveSettings**](MoveSettings.md)

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


## getRentalEmails

> OutlookEmailsPage getRentalEmails(page, year)



### Example

```ts
import {
  Configuration,
  OutlookControllerApi,
} from '';
import type { GetRentalEmailsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new OutlookControllerApi();

  const body = {
    // number (optional)
    page: 56,
    // number (optional)
    year: 56,
  } satisfies GetRentalEmailsRequest;

  try {
    const data = await api.getRentalEmails(body);
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
| **page** | `number` |  | [Optional] [Defaults to `0`] |
| **year** | `number` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**OutlookEmailsPage**](OutlookEmailsPage.md)

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


## parseEmail

> { [key: string]: any; } parseEmail(messageId, requestBody)



### Example

```ts
import {
  Configuration,
  OutlookControllerApi,
} from '';
import type { ParseEmailRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new OutlookControllerApi();

  const body = {
    // string
    messageId: messageId_example,
    // { [key: string]: string; }
    requestBody: Object,
  } satisfies ParseEmailRequest;

  try {
    const data = await api.parseEmail(body);
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
| **messageId** | `string` |  | [Defaults to `undefined`] |
| **requestBody** | `{ [key: string]: string; }` |  | |

### Return type

**{ [key: string]: any; }**

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


## status

> { [key: string]: boolean; } status()



### Example

```ts
import {
  Configuration,
  OutlookControllerApi,
} from '';
import type { StatusRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new OutlookControllerApi();

  try {
    const data = await api.status();
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

**{ [key: string]: boolean; }**

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


## updateConfiguredFolderSettings

> Array&lt;FolderSetting&gt; updateConfiguredFolderSettings(requestBody)



### Example

```ts
import {
  Configuration,
  OutlookControllerApi,
} from '';
import type { UpdateConfiguredFolderSettingsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new OutlookControllerApi();

  const body = {
    // { [key: string]: Array<FolderSetting>; }
    requestBody: Object,
  } satisfies UpdateConfiguredFolderSettingsRequest;

  try {
    const data = await api.updateConfiguredFolderSettings(body);
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
| **requestBody** | `{ [key: string]: Array<FolderSetting>; }` |  | |

### Return type

[**Array&lt;FolderSetting&gt;**](FolderSetting.md)

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


## updateMoveSettings

> updateMoveSettings(requestBody)



### Example

```ts
import {
  Configuration,
  OutlookControllerApi,
} from '';
import type { UpdateMoveSettingsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new OutlookControllerApi();

  const body = {
    // { [key: string]: any; }
    requestBody: Object,
  } satisfies UpdateMoveSettingsRequest;

  try {
    const data = await api.updateMoveSettings(body);
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
| **requestBody** | `{ [key: string]: any; }` |  | |

### Return type

`void` (Empty response body)

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
| **204** | No Content |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

