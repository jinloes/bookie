# BackupStatus

## Properties

| Name           | Type    |
| -------------- | ------- |
| `vendor`       | string  |
| `frozen`       | boolean |
| `keyConfirmed` | boolean |

## Example

```typescript
import type { BackupStatus } from '';

// TODO: Update the object below with actual values
const example = {
  vendor: null,
  frozen: null,
  keyConfirmed: null,
} satisfies BackupStatus;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BackupStatus;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
