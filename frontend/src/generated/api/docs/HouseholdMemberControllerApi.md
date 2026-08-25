# HouseholdMemberControllerApi

All URIs are relative to _http://localhost:48763_

| Method                                                                             | HTTP request                        | Description |
| ---------------------------------------------------------------------------------- | ----------------------------------- | ----------- |
| [**createHouseholdMember**](HouseholdMemberControllerApi.md#createhouseholdmember) | **POST** /api/household-members     |             |
| [**getHouseholdMembers**](HouseholdMemberControllerApi.md#gethouseholdmembers)     | **GET** /api/household-members      |             |
| [**updateHouseholdMember**](HouseholdMemberControllerApi.md#updatehouseholdmember) | **PUT** /api/household-members/{id} |             |

## createHouseholdMember

> HouseholdMemberResponse createHouseholdMember(upsertHouseholdMemberRequest)

### Example

```ts
import {
  Configuration,
  HouseholdMemberControllerApi,
} from '';
import type { CreateHouseholdMemberRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new HouseholdMemberControllerApi();

  const body = {
    // UpsertHouseholdMemberRequest
    upsertHouseholdMemberRequest: ...,
  } satisfies CreateHouseholdMemberRequest;

  try {
    const data = await api.createHouseholdMember(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                             | Type                                                            | Description | Notes |
| -------------------------------- | --------------------------------------------------------------- | ----------- | ----- |
| **upsertHouseholdMemberRequest** | [UpsertHouseholdMemberRequest](UpsertHouseholdMemberRequest.md) |             |       |

### Return type

[**HouseholdMemberResponse**](HouseholdMemberResponse.md)

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

## getHouseholdMembers

> Array&lt;HouseholdMemberResponse&gt; getHouseholdMembers()

### Example

```ts
import { Configuration, HouseholdMemberControllerApi } from '';
import type { GetHouseholdMembersRequest } from '';

async function example() {
  console.log('🚀 Testing  SDK...');
  const api = new HouseholdMemberControllerApi();

  try {
    const data = await api.getHouseholdMembers();
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

[**Array&lt;HouseholdMemberResponse&gt;**](HouseholdMemberResponse.md)

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

## updateHouseholdMember

> HouseholdMemberResponse updateHouseholdMember(id, upsertHouseholdMemberRequest)

### Example

```ts
import {
  Configuration,
  HouseholdMemberControllerApi,
} from '';
import type { UpdateHouseholdMemberRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const api = new HouseholdMemberControllerApi();

  const body = {
    // number
    id: 789,
    // UpsertHouseholdMemberRequest
    upsertHouseholdMemberRequest: ...,
  } satisfies UpdateHouseholdMemberRequest;

  try {
    const data = await api.updateHouseholdMember(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

| Name                             | Type                                                            | Description | Notes                     |
| -------------------------------- | --------------------------------------------------------------- | ----------- | ------------------------- |
| **id**                           | `number`                                                        |             | [Defaults to `undefined`] |
| **upsertHouseholdMemberRequest** | [UpsertHouseholdMemberRequest](UpsertHouseholdMemberRequest.md) |             |                           |

### Return type

[**HouseholdMemberResponse**](HouseholdMemberResponse.md)

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
