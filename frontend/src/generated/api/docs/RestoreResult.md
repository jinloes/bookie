# RestoreResult

## Properties

| Name              | Type    |
| ----------------- | ------- |
| `restoreId`       | string  |
| `state`           | string  |
| `restored`        | boolean |
| `validated`       | boolean |
| `restartRequired` | boolean |
| `message`         | string  |

## Example

```typescript
import type { RestoreResult } from '';

// TODO: Update the object below with actual values
const example = {
  restoreId: null,
  state: null,
  restored: null,
  validated: null,
  restartRequired: null,
  message: null,
} satisfies RestoreResult;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RestoreResult;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
