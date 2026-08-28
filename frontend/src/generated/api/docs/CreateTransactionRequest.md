# CreateTransactionRequest

## Properties

| Name                   | Type   |
| ---------------------- | ------ |
| `amount`               | number |
| `direction`            | string |
| `date`                 | string |
| `description`          | string |
| `activityId`           | number |
| `neutralCategoryId`    | number |
| `counterpartyId`       | number |
| `origin`               | string |
| `externalId`           | string |
| `sourceLabel`          | string |
| `attachmentExternalId` | string |
| `attachmentFileName`   | string |
| `attachmentSha256`     | string |

## Example

```typescript
import type { CreateTransactionRequest } from '';

// TODO: Update the object below with actual values
const example = {
  amount: null,
  direction: null,
  date: null,
  description: null,
  activityId: null,
  neutralCategoryId: null,
  counterpartyId: null,
  origin: null,
  externalId: null,
  sourceLabel: null,
  attachmentExternalId: null,
  attachmentFileName: null,
  attachmentSha256: null,
} satisfies CreateTransactionRequest;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CreateTransactionRequest;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
