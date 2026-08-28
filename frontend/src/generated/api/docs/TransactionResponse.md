# TransactionResponse

## Properties

| Name               | Type                                                                                     |
| ------------------ | ---------------------------------------------------------------------------------------- |
| `id`               | number                                                                                   |
| `amount`           | number                                                                                   |
| `direction`        | string                                                                                   |
| `date`             | string                                                                                   |
| `description`      | string                                                                                   |
| `activity`         | [FinancialActivityResponse](FinancialActivityResponse.md)                                |
| `propertyId`       | number                                                                                   |
| `category`         | [NeutralCategoryResponse](NeutralCategoryResponse.md)                                    |
| `counterpartyId`   | number                                                                                   |
| `attachments`      | [Array&lt;TransactionAttachmentResponse&gt;](TransactionAttachmentResponse.md)           |
| `importReferences` | [Array&lt;TransactionImportReferenceResponse&gt;](TransactionImportReferenceResponse.md) |
| `createdAt`        | string                                                                                   |
| `updatedAt`        | string                                                                                   |
| `version`          | number                                                                                   |

## Example

```typescript
import type { TransactionResponse } from '';

// TODO: Update the object below with actual values
const example = {
  id: null,
  amount: null,
  direction: null,
  date: null,
  description: null,
  activity: null,
  propertyId: null,
  category: null,
  counterpartyId: null,
  attachments: null,
  importReferences: null,
  createdAt: null,
  updatedAt: null,
  version: null,
} satisfies TransactionResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TransactionResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
