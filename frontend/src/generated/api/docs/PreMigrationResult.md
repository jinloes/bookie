# PreMigrationResult

## Properties

| Name             | Type                                     |
| ---------------- | ---------------------------------------- |
| `uploaded`       | [Array&lt;BackupFile&gt;](BackupFile.md) |
| `localDirectory` | string                                   |

## Example

```typescript
import type { PreMigrationResult } from '';

// TODO: Update the object below with actual values
const example = {
  uploaded: null,
  localDirectory: null,
} satisfies PreMigrationResult;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PreMigrationResult;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
