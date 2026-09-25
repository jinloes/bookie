# InboxItemResponse

## Properties

| Name                      | Type                                                      |
| ------------------------- | --------------------------------------------------------- |
| `id`                      | number                                                    |
| `origin`                  | string                                                    |
| `sourceId`                | string                                                    |
| `immutableSourceId`       | string                                                    |
| `state`                   | string                                                    |
| `externalSyncState`       | string                                                    |
| `proposedDirection`       | string                                                    |
| `subject`                 | string                                                    |
| `amount`                  | number                                                    |
| `description`             | string                                                    |
| `date`                    | string                                                    |
| `proposedCategory`        | string                                                    |
| `propertyName`            | string                                                    |
| `counterpartyName`        | string                                                    |
| `sourceLabel`             | string                                                    |
| `activity`                | [FinancialActivityResponse](FinancialActivityResponse.md) |
| `category`                | [FinancialCategoryResponse](FinancialCategoryResponse.md) |
| `counterpartyId`          | number                                                    |
| `classificationAmbiguous` | boolean                                                   |
| `receiptExternalId`       | string                                                    |
| `receiptFileName`         | string                                                    |
| `financialTransactionId`  | number                                                    |
| `errorMessage`            | string                                                    |
| `createdAt`               | string                                                    |
| `updatedAt`               | string                                                    |
| `version`                 | number                                                    |

## Example

```typescript
import type { InboxItemResponse } from '';

// TODO: Update the object below with actual values
const example = {
  id: null,
  origin: null,
  sourceId: null,
  immutableSourceId: null,
  state: null,
  externalSyncState: null,
  proposedDirection: null,
  subject: null,
  amount: null,
  description: null,
  date: null,
  proposedCategory: null,
  propertyName: null,
  counterpartyName: null,
  sourceLabel: null,
  activity: null,
  category: null,
  counterpartyId: null,
  classificationAmbiguous: null,
  receiptExternalId: null,
  receiptFileName: null,
  financialTransactionId: null,
  errorMessage: null,
  createdAt: null,
  updatedAt: null,
  version: null,
} satisfies InboxItemResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as InboxItemResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
