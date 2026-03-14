import { BrowserRouter, Routes, Route, Link, useLocation } from 'react-router-dom';
import {
  Box, Drawer, List, ListItemButton, ListItemIcon, ListItemText,
  Toolbar, AppBar, Typography, CssBaseline, ThemeProvider, createTheme,
} from '@mui/material';
import StorefrontIcon from '@mui/icons-material/Storefront';
import DashboardIcon from '@mui/icons-material/Dashboard';
import Storefront from './pages/Storefront';
import AdminDashboard from './pages/AdminDashboard';

const drawerWidth = 240;

const theme = createTheme({
  palette: {
    primary: { main: '#1976d2' },
    secondary: { main: '#dc004e' },
  },
});

function NavContent() {
  const location = useLocation();

  const navItems = [
    { text: 'Storefront', icon: <StorefrontIcon />, path: '/' },
    { text: 'Admin Dashboard', icon: <DashboardIcon />, path: '/admin' },
  ];

  return (
    <List>
      {navItems.map((item) => (
        <ListItemButton
          key={item.text}
          component={Link}
          to={item.path}
          selected={location.pathname === item.path}
        >
          <ListItemIcon>{item.icon}</ListItemIcon>
          <ListItemText primary={item.text} />
        </ListItemButton>
      ))}
    </List>
  );
}

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Box sx={{ display: 'flex' }}>
          <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
            <Toolbar>
              <Typography variant="h6" noWrap>
                Marketplace Platform
              </Typography>
            </Toolbar>
          </AppBar>

          <Drawer
            variant="permanent"
            sx={{
              width: drawerWidth,
              '& .MuiDrawer-paper': { width: drawerWidth, boxSizing: 'border-box' },
            }}
          >
            <Toolbar />
            <NavContent />
          </Drawer>

          <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
            <Toolbar />
            <Routes>
              <Route path="/" element={<Storefront />} />
              <Route path="/admin" element={<AdminDashboard />} />
            </Routes>
          </Box>
        </Box>
      </BrowserRouter>
    </ThemeProvider>
  );
}
