# TransactionImportReferenceResponse

## Properties

| Name          | Type   |
| ------------- | ------ |
| `id`          | number |
| `origin`      | string |
| `externalId`  | string |
| `sourceLabel` | string |

## Example

```typescript
import type { TransactionImportReferenceResponse } from '';

// TODO: Update the object below with actual values
const example = {
  id: null,
  origin: null,
  externalId: null,
  sourceLabel: null,
} satisfies TransactionImportReferenceResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TransactionImportReferenceResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
