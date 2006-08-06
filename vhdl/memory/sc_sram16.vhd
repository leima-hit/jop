--
--	sc_sram16.vhd
--
--	SimpCon compliant external memory interface
--	for 16-bit SRAM (e.g. Altera DE2 board)
--
--	High 16-bit word is at lower address
--
--	Connection between mem_sc and the external memory bus
--
--	memory mapping
--	
--		000000-x7ffff	external SRAM (w mirror)	max. 512 kW (4*4 MBit)
--
--	RAM: 16 bit word
--
--
--	2006-08-01	Adapted from sc_ram32.vhd
--

Library IEEE;
use IEEE.std_logic_1164.all;
use ieee.numeric_std.all;

use work.jop_types.all;

entity sc_mem_if is
generic (ram_ws : integer; addr_bits : integer);

port (

	clk, reset	: in std_logic;

-- SimpCon interface

	address		: in std_logic_vector(addr_bits-1 downto 0);
	wr_data		: in std_logic_vector(31 downto 0);
	rd, wr		: in std_logic;
	rd_data		: out std_logic_vector(31 downto 0);
	rdy_cnt		: out unsigned(1 downto 0);

-- memory interface

	ram_addr	: out std_logic_vector(addr_bits-1 downto 0);
	ram_dout	: out std_logic_vector(15 downto 0);
	ram_din		: in std_logic_vector(15 downto 0);
	ram_dout_en	: out std_logic;
	ram_ncs		: out std_logic;
	ram_noe		: out std_logic;
	ram_nwe		: out std_logic

);
end sc_mem_if;

architecture rtl of sc_mem_if is

--
--	signals for mem interface
--
	type state_type		is (
							idl, rd1_h, rd2_h, rd1_l, rd2_l,
							wr_h, wr_idl, wr_l
						);
	signal state 		: state_type;
	signal next_state	: state_type;

	signal nwr_int		: std_logic;
	signal wait_state	: unsigned(3 downto 0);
	signal cnt			: unsigned(1 downto 0);

	signal dout_ena		: std_logic;
	signal rd_data_ena_h	: std_logic;
	signal rd_data_ena_l	: std_logic;
	signal inc_addr			: std_logic;
	signal wr_low			: std_logic;

	signal ram_dout_low	: std_logic_vector(15 downto 0);
	signal ram_din_low		: std_logic_vector(15 downto 0);


begin

	ram_dout_en <= dout_ena;

	rdy_cnt <= cnt;

--
--	Register memory address, write data and read data
--
process(clk, reset)
begin
	if reset='1' then

		ram_addr <= (others => '0');
		ram_dout <= (others => '0');
		rd_data <= (others => '0');
		ram_dout_low <= (others => '0');

	elsif rising_edge(clk) then

		if rd='1' or wr='1' then
			ram_addr <= address(addr_bits-2 downto 0) & "0";
		end if;
		if inc_addr='1' then
			ram_addr(0) <= '1';
		end if;
		if wr='1' then
			ram_dout <= wr_data(31 downto 16);
			ram_dout_low <= wr_data(15 downto 0);
		end if;
		if wr_low='1' then
			ram_dout <= ram_dout_low;
		end if;
		if rd_data_ena_h='1' then
			rd_data(31 downto 16) <= ram_din;
		end if;
		if rd_data_ena_l='1' then
			rd_data(15 downto 0) <= ram_din;
		end if;

	end if;
end process;

--
--	'delay' nwe 1/2 cycle -> change on falling edge
--
process(clk, reset)

begin
	if (reset='1') then
		ram_nwe <= '1';
	elsif falling_edge(clk) then
		ram_nwe <= nwr_int;
	end if;

end process;


--
--	next state logic
--
process(state, rd, wr, wait_state)

begin

	next_state <= state;

	case state is

		when idl =>
			if rd='1' then
				if ram_ws=0 then
					-- then we omit state rd1!
					next_state <= rd2_h;
				else
					next_state <= rd1_h;
				end if;
			elsif wr='1' then
				next_state <= wr_h;
			end if;

		-- the WS state
		when rd1_h =>
			if wait_state=2 then
				next_state <= rd2_h;
			end if;

		when rd2_h =>
			-- go to read low word
			if ram_ws=0 then
				-- then we omit state rd1!
				next_state <= rd2_l;
			else
				next_state <= rd1_l;
			end if;

		-- the WS state
		when rd1_l =>
			if wait_state=2 then
				next_state <= rd2_l;
			end if;

		-- last read state
		when rd2_l =>
			next_state <= idl;
			-- This should do to give us a pipeline
			-- level of 2 for read
			if rd='1' then
				if ram_ws=0 then
					-- then we omit state rd1!
					next_state <= rd2_h;
				else
					next_state <= rd1_h;
				end if;
			elsif wr='1' then
				next_state <= wr_h;
			end if;
			
		-- the WS state
		when wr_h =>
-- TODO: check what happens on ram_ws=0
-- TODO: do we need a write pipelining?
--	not at the moment, but parhaps later when
--	we write the stack content to main memory
			if wait_state=1 then
				next_state <= wr_idl;
			end if;

		-- one idle state for nwr to go high
		when wr_idl =>
			next_state <= wr_l;

		-- the WS state
		when wr_l =>
			if wait_state=1 then
				next_state <= idl;
			end if;

	end case;
				
end process;

--
--	state machine register
--	output register
--
process(clk, reset)

begin
	if (reset='1') then
		state <= idl;
		dout_ena <= '0';
		ram_ncs <= '1';
		ram_noe <= '1';
		rd_data_ena_h <= '0';
		rd_data_ena_l <= '0';
		inc_addr <= '0';
		wr_low <= '0';
	elsif rising_edge(clk) then

		state <= next_state;
		dout_ena <= '0';
		ram_ncs <= '1';
		ram_noe <= '1';
		rd_data_ena_h <= '0';
		rd_data_ena_l <= '0';
		inc_addr <= '0';
		wr_low <= '0';

		case next_state is

			when idl =>

			-- the wait state
			when rd1_h =>
				ram_ncs <= '0';
				ram_noe <= '0';

			-- high word last read state
			when rd2_h =>
				ram_ncs <= '0';
				ram_noe <= '0';
				rd_data_ena_h <= '1';
				inc_addr <= '1';
				
			-- the wait state
			when rd1_l =>
				ram_ncs <= '0';
				ram_noe <= '0';

			-- low word last read state
			when rd2_l =>
				ram_ncs <= '0';
				ram_noe <= '0';
				rd_data_ena_l <= '1';
				
			-- the WS state
			when wr_h =>
				ram_ncs <= '0';
				dout_ena <= '1';

			-- high word last write state
			when wr_idl =>
				ram_ncs <= '0';
				dout_ena <= '1';
				inc_addr <= '1';
				wr_low <= '1';

			-- the WS state
			when wr_l =>
				ram_ncs <= '0';
				dout_ena <= '1';

		end case;
					
	end if;
end process;

--
--	nwr combinatorial processing
--	for the negativ edge
--
process(next_state, state)
begin

	nwr_int <= '1';
	if next_state=wr_l or next_state=wr_h then
		nwr_int <= '0';
	end if;

end process;

--
-- wait_state processing
--
process(clk, reset)
begin
	if (reset='1') then
		wait_state <= (others => '1');
		cnt <= "00";
	elsif rising_edge(clk) then

		wait_state <= wait_state-1;

		cnt <= "11";
		if next_state=idl then
			cnt <= "00";
		end if;

		if rd='1' or wr='1' then
			wait_state <= to_unsigned(ram_ws+1, 4);
			cnt <= "11";
		end if;

		if state=rd2_h or state=wr_idl then
			wait_state <= to_unsigned(ram_ws+1, 4);
			if ram_ws<3 then
				cnt <= to_unsigned(ram_ws+1, 2);
			else
				cnt <= "11";
			end if;
		end if;

		if state=rd1_l or state=rd2_l or state=wr_l then
			-- if wait_state<4 then
			if wait_state(3 downto 2)="00" then
				cnt <= wait_state(1 downto 0)-1;
			end if;
		end if;

	end if;
end process;

end rtl;
