import React, { useState } from 'react';
import { Stack, Anchor, Collapse, Group, Badge, Text } from '@mantine/core';

/**
 * Renders a list of badges behind a show/hide toggle.
 *
 * @param {{
 *   items?: any[],
 *   color: string,
 *   variant?: string,
 *   getKey: (item: any) => string | number,
 *   getLabel: (item: any) => React.ReactNode,
 *   getTitle?: (item: any) => string | undefined,
 * }} props
 */
export default function CollapsibleBadges({
  items,
  color,
  variant = 'outline',
  getKey,
  getLabel,
  getTitle,
}) {
  const [open, setOpen] = useState(false);
  if (!items?.length)
    return (
      <Text c="dimmed" size="sm">
        —
      </Text>
    );
  return (
    <Stack gap={4}>
      <Anchor size="sm" onClick={() => setOpen((o) => !o)}>
        {open ? `Hide (${items.length})` : `Show ${items.length}`}
      </Anchor>
      <Collapse expanded={open}>
        <Group gap={4} wrap="wrap">
          {items.map((item) => (
            <Badge
              key={getKey(item)}
              variant={variant}
              color={color}
              size="sm"
              title={getTitle?.(item)}
            >
              {getLabel(item)}
            </Badge>
          ))}
        </Group>
      </Collapse>
    </Stack>
  );
}
