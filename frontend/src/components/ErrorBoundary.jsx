import { Alert, Box, Button, Stack, Text } from '@mantine/core';
import { IconAlertCircle } from '@tabler/icons-react';
import React from 'react';

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    console.error('App error:', error, errorInfo);
    this.setState({
      error,
      errorInfo,
    });
  }

  handleReload = () => {
    window.location.href = '/';
  };

  render() {
    if (this.state.hasError) {
      return (
        <Box p="xl" style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Stack gap="lg" style={{ maxWidth: 600 }}>
            <Box style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <IconAlertCircle size={32} color="red" />
              <Text size="xl" fw={600} c="red">
                Oops! Something went wrong
              </Text>
            </Box>

            <Alert title="Error Details" color="red" icon={<IconAlertCircle />}>
              <Text size="sm" c="gray.8" mb="xs">
                {this.state.error?.message || 'An unexpected error occurred'}
              </Text>
              {process.env.NODE_ENV === 'development' && this.state.errorInfo && (
                <details style={{ marginTop: 12, fontSize: 12, whiteSpace: 'pre-wrap' }}>
                  <summary style={{ cursor: 'pointer', marginBottom: 8 }}>
                    Stack trace (dev only)
                  </summary>
                  <code style={{ display: 'block', overflow: 'auto', maxHeight: 200 }}>
                    {this.state.errorInfo.componentStack}
                  </code>
                </details>
              )}
            </Alert>

            <Stack gap="sm">
              <Button onClick={this.handleReload} color="blue">
                Reload App
              </Button>
              <Text size="xs" c="gray.6">
                If this problem persists, try clearing your browser cache and restarting the application.
              </Text>
            </Stack>
          </Stack>
        </Box>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
