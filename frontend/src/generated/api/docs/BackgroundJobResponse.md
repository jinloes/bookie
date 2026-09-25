# BackgroundJobResponse

## Properties

| Name             | Type   |
| ---------------- | ------ |
| `id`             | number |
| `inboxItemId`    | number |
| `type`           | string |
| `state`          | string |
| `attempts`       | number |
| `maxAttempts`    | number |
| `availableAt`    | string |
| `leaseExpiresAt` | string |
| `lastError`      | string |
| `terminalReason` | string |
| `createdAt`      | string |
| `updatedAt`      | string |
| `version`        | number |

## Example

```typescript
import type { BackgroundJobResponse } from '';

// TODO: Update the object below with actual values
const example = {
  id: null,
  inboxItemId: null,
  type: null,
  state: null,
  attempts: null,
  maxAttempts: null,
  availableAt: null,
  leaseExpiresAt: null,
  lastError: null,
  terminalReason: null,
  createdAt: null,
  updatedAt: null,
  version: null,
} satisfies BackgroundJobResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BackgroundJobResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
