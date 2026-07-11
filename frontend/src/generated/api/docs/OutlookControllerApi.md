# OutlookControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                                 | HTTP request                                    | Description |
| -------------------------------------------------------------------------------------- | ----------------------------------------------- | ----------- |
| [**connectOutlook**](OutlookControllerApi.md#connectoutlook)                           | **GET** /api/outlook/connect                    |             |
| [**getOutlookAvailableFolders**](OutlookControllerApi.md#getoutlookavailablefolders)   | **GET** /api/outlook/folders/available          |             |
| [**getOutlookEmailContent**](OutlookControllerApi.md#getoutlookemailcontent)           | **GET** /api/outlook/emails/{messageId}/content |             |
| [**getOutlookFolderSettings**](OutlookControllerApi.md#getoutlookfoldersettings)       | **GET** /api/outlook/settings/folders           |             |
| [**getOutlookMoveSettings**](OutlookControllerApi.md#getoutlookmovesettings)           | **GET** /api/outlook/settings/move              |             |
| [**getOutlookRentalEmails**](OutlookControllerApi.md#getoutlookrentalemails)           | **GET** /api/outlook/emails/rental              |             |
| [**getOutlookStatus**](OutlookControllerApi.md#getoutlookstatus)                       | **GET** /api/outlook/status                     |             |
| [**outlookCallback**](OutlookControllerApi.md#outlookcallback)                         | **GET** /api/outlook/callback                   |             |
| [**parseOutlookEmail**](OutlookControllerApi.md#parseoutlookemail)                     | **POST** /api/outlook/emails/{messageId}/parse  |             |
| [**updateOutlookFolderSettings**](OutlookControllerApi.md#updateoutlookfoldersettings) | **PUT** /api/outlook/settings/folders           |             |
| [**updateOutlookMoveSettings**](OutlookControllerApi.md#updateoutlookmovesettings)     | **PUT** /api/outlook/settings/move              |             |

## connectOutlook

> RedirectView connectOutlook()

### Example

```ts
import { Configuration, OutlookControllerApi } from '';
import type { ConnectOutlookRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new OutlookControllerApi();

  try {
    const data = await api.connectOutlook();
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

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getOutlookAvailableFolders

> Array&lt;FolderInfo&gt; getOutlookAvailableFolders()

### Example

```ts
import { Configuration, OutlookControllerApi } from '';
import type { GetOutlookAvailableFoldersRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new OutlookControllerApi();

  try {
    const data = await api.getOutlookAvailableFolders();
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

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getOutlookEmailContent

> MessageContent getOutlookEmailContent(messageId)

### Example

```ts
import { Configuration, OutlookControllerApi } from '';
import type { GetOutlookEmailContentRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new OutlookControllerApi();

  const body = {
    // string
    messageId: messageId_example,
  } satisfies GetOutlookEmailContentRequest;

  try {
    const data = await api.getOutlookEmailContent(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name          | Type     | Description | Notes                     |
| ------------- | -------- | ----------- | ------------------------- |
| **messageId** | `string` |             | [Defaults to `undefined`] |

### Return type

[**MessageContent**](MessageContent.md)

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

## getOutlookFolderSettings

> Array&lt;FolderSetting&gt; getOutlookFolderSettings()

### Example

```ts
import { Configuration, OutlookControllerApi } from '';
import type { GetOutlookFolderSettingsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new OutlookControllerApi();

  try {
    const data = await api.getOutlookFolderSettings();
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

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getOutlookMoveSettings

> MoveSettings getOutlookMoveSettings()

### Example

```ts
import { Configuration, OutlookControllerApi } from '';
import type { GetOutlookMoveSettingsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new OutlookControllerApi();

  try {
    const data = await api.getOutlookMoveSettings();
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

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## getOutlookRentalEmails

> OutlookEmailsPage getOutlookRentalEmails(page, year)

### Example

```ts
import { Configuration, OutlookControllerApi } from '';
import type { GetOutlookRentalEmailsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new OutlookControllerApi();

  const body = {
    // number (optional)
    page: 56,
    // number (optional)
    year: 56,
  } satisfies GetOutlookRentalEmailsRequest;

  try {
    const data = await api.getOutlookRentalEmails(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name     | Type     | Description | Notes                                |
| -------- | -------- | ----------- | ------------------------------------ |
| **page** | `number` |             | [Optional] [Defaults to `0`]         |
| **year** | `number` |             | [Optional] [Defaults to `undefined`] |

### Return type

[**OutlookEmailsPage**](OutlookEmailsPage.md)

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

## getOutlookStatus

> { [key: string]: boolean; } getOutlookStatus()

### Example

```ts
import { Configuration, OutlookControllerApi } from '';
import type { GetOutlookStatusRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new OutlookControllerApi();

  try {
    const data = await api.getOutlookStatus();
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

| Status code | Description           | Response headers |
| ----------- | --------------------- | ---------------- |
| **400**     | Bad Request           | -                |
| **409**     | Conflict              | -                |
| **500**     | Internal Server Error | -                |
| **200**     | OK                    | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

## outlookCallback

> string outlookCallback(code, state, error, errorDescription)

### Example

```ts
import { Configuration, OutlookControllerApi } from '';
import type { OutlookCallbackRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
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
  } satisfies OutlookCallbackRequest;

  try {
    const data = await api.outlookCallback(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                 | Type     | Description | Notes                                |
| -------------------- | -------- | ----------- | ------------------------------------ |
| **code**             | `string` |             | [Optional] [Defaults to `undefined`] |
| **state**            | `string` |             | [Optional] [Defaults to `undefined`] |
| **error**            | `string` |             | [Optional] [Defaults to `undefined`] |
| **errorDescription** | `string` |             | [Optional] [Defaults to `undefined`] |

### Return type

**string**

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

## parseOutlookEmail

> { [key: string]: any; } parseOutlookEmail(messageId, requestBody)

### Example

```ts
import { Configuration, OutlookControllerApi } from '';
import type { ParseOutlookEmailRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new OutlookControllerApi();

  const body = {
    // string
    messageId: messageId_example,
    // { [key: string]: string; }
    requestBody: Object,
  } satisfies ParseOutlookEmailRequest;

  try {
    const data = await api.parseOutlookEmail(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name            | Type                         | Description | Notes                     |
| --------------- | ---------------------------- | ----------- | ------------------------- |
| **messageId**   | `string`                     |             | [Defaults to `undefined`] |
| **requestBody** | `{ [key: string]: string; }` |             |                           |

### Return type

**{ [key: string]: any; }**

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

## updateOutlookFolderSettings

> Array&lt;FolderSetting&gt; updateOutlookFolderSettings(requestBody)

### Example

```ts
import { Configuration, OutlookControllerApi } from '';
import type { UpdateOutlookFolderSettingsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new OutlookControllerApi();

  const body = {
    // { [key: string]: Array<FolderSetting>; }
    requestBody: Object,
  } satisfies UpdateOutlookFolderSettingsRequest;

  try {
    const data = await api.updateOutlookFolderSettings(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name            | Type                                       | Description | Notes |
| --------------- | ------------------------------------------ | ----------- | ----- |
| **requestBody** | `{ [key: string]: Array<FolderSetting>; }` |             |       |

### Return type

[**Array&lt;FolderSetting&gt;**](FolderSetting.md)

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

## updateOutlookMoveSettings

> updateOutlookMoveSettings(requestBody)

### Example

```ts
import { Configuration, OutlookControllerApi } from '';
import type { UpdateOutlookMoveSettingsRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new OutlookControllerApi();

  const body = {
    // { [key: string]: any; }
    requestBody: Object,
  } satisfies UpdateOutlookMoveSettingsRequest;

  try {
    const data = await api.updateOutlookMoveSettings(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name            | Type                      | Description | Notes |
| --------------- | ------------------------- | ----------- | ----- |
| **requestBody** | `{ [key: string]: any; }` |             |       |

### Return type

`void` (Empty response body)

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
| **204**     | No Content            | -                |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
