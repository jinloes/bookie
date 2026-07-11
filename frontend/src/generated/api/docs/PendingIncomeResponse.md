# PendingIncomeResponse

## Properties

| Name          | Type                                          |
| ------------- | --------------------------------------------- |
| `id`          | number                                        |
| `amount`      | number                                        |
| `description` | string                                        |
| `date`        | string                                        |
| `source`      | string                                        |
| `sourceId`    | string                                        |
| `sourceType`  | string                                        |
| `status`      | string                                        |
| `createdAt`   | string                                        |
| `property`    | [PropertyRefResponse](PropertyRefResponse.md) |
| `payer`       | [PayerRefResponse](PayerRefResponse.md)       |

## Example

```typescript
import type { PendingIncomeResponse } from '';

// TODO: Update the object below with actual values
const example = {
  id: null,
  amount: null,
  description: null,
  date: null,
  source: null,
  sourceId: null,
  sourceType: null,
  status: null,
  createdAt: null,
  property: null,
  payer: null,
} satisfies PendingIncomeResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PendingIncomeResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
