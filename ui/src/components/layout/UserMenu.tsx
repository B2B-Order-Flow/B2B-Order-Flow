import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import LogoutIcon from '@mui/icons-material/Logout';
import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Divider from '@mui/material/Divider';
import ListItemIcon from '@mui/material/ListItemIcon';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { useColorMode } from '../../theme/ColorModeProvider';
import { c } from '../../theme/tokens';
import { Avatar } from '../ui/Brand';

// Аватар в шапке открывает меню пользователя: имя и логин, смена темы, выход.
export function UserMenu() {
  const { user, logout } = useSession();
  const { mode, toggle } = useColorMode();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const close = () => setAnchor(null);

  return (
    <>
      <ButtonBase
        onClick={(event) => setAnchor(event.currentTarget)}
        aria-label={texts.session.userMenu}
        aria-haspopup="menu"
        data-testid="user-menu"
        sx={{ borderRadius: '50%', flexShrink: 0 }}
      >
        <Avatar name={user?.fullName} />
      </ButtonBase>
      <Menu
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ paper: { sx: { minWidth: 240 } } }}
      >
        <Box sx={{ px: 2, py: 1 }}>
          <Typography variant="subtitle1">{user?.fullName}</Typography>
          <Typography variant="caption" sx={{ color: c('text-muted') }}>
            {user?.login}
          </Typography>
        </Box>
        <Divider />
        <MenuItem onClick={toggle} data-testid="theme-toggle">
          <ListItemIcon>
            {mode === 'light' ? <DarkModeOutlinedIcon fontSize="small" /> : <LightModeOutlinedIcon fontSize="small" />}
          </ListItemIcon>
          {mode === 'light' ? texts.session.darkTheme : texts.session.lightTheme}
        </MenuItem>
        <MenuItem
          onClick={() => {
            close();
            logout();
          }}
          data-testid="logout"
        >
          <ListItemIcon>
            <LogoutIcon fontSize="small" />
          </ListItemIcon>
          {texts.session.logout}
        </MenuItem>
      </Menu>
    </>
  );
}
