# TransactionAttachmentResponse

## Properties

| Name              | Type   |
| ----------------- | ------ |
| `id`              | number |
| `storageProvider` | string |
| `externalId`      | string |
| `fileName`        | string |
| `sha256`          | string |

## Example

```typescript
import type { TransactionAttachmentResponse } from '';

// TODO: Update the object below with actual values
const example = {
  id: null,
  storageProvider: null,
  externalId: null,
  fileName: null,
  sha256: null,
} satisfies TransactionAttachmentResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TransactionAttachmentResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
