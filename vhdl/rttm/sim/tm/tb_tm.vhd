library std;
library ieee;

use std.textio.all;
use ieee.std_logic_textio.all;

use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

use ieee.math_real.log2;
use ieee.math_real.ceil;

library modelsim_lib;
use modelsim_lib.util.all;

use work.sc_pack.all;
use work.sc_arbiter_pack.all;
use work.tm_pack.all;
use work.tm_internal_pack.all;


entity tb_tm is
end tb_tm;

architecture behav of tb_tm is


--
--	Settings
--

constant MEM_BITS			: integer := 15;

constant addr_width		: integer := 18;	-- address bits of cachable memory
constant way_bits		: integer := 3;		-- 2**way_bits is number of entries


constant conflict_detection_cycles		: integer := 5;

--
--	Generic
--

signal finished				: boolean := false;

signal clk					: std_logic := '1';
signal reset				: std_logic;

constant cycle				: time := 10 ns;
constant delta				: time := cycle/2;
constant reset_time			: time := 5 ns;

--
--	Testbench
--

	signal commit_out_try: std_logic;
	signal commit_in_allow: std_logic;
	
	signal sc_out_cpu: sc_out_type;
	signal sc_in_cpu: sc_in_type;
	signal sc_out_arb: sc_out_type;
	signal sc_in_arb: sc_in_type;		
	signal exc_tm_rollback: std_logic;

	signal broadcast: tm_broadcast_type := 
		(
			valid => '0',
			address => (others => 'U')
		);

--
--	Test activity flags
--

	signal testing_commit: boolean := false;
	signal testing_conflict: boolean := false;


--
--	ModelSim Signal Spy
--

	-- TODO compiler did not allow this signal as external name	
	signal write_buffer: data_array(0 to 2**way_bits-1);
	
begin

--
--	Testbench
--

	dut: entity work.tmif(rtl)
	generic map (
		addr_width => addr_width,
		way_bits => way_bits
	)	
	port map (
		clk => clk,
		reset => reset,
		commit_out_try => commit_out_try,
		commit_in_allow => commit_in_allow,
		broadcast => broadcast,
		sc_out_cpu => sc_out_cpu,
		sc_in_cpu => sc_in_cpu,
		sc_out_arb => sc_out_arb,
		sc_in_arb => sc_in_arb,
		exc_tm_rollback => exc_tm_rollback
		);
		
	-- TODO why is this ignored?
	sc_out_cpu.nc <= '1';

	commit_coordinator: process is
	begin
		commit_in_allow <= '0';
	
		wait until commit_out_try = '1';
		wait for cycle * 3;
				
		commit_in_allow <= '1';
		
		wait until commit_out_try = '0';
		
		wait for cycle;
		commit_in_allow <= '0'; 
	end process commit_coordinator;

	signal_spy: process is
	begin
		init_signal_spy("/tb_tm/dut/cmp_tm/data","/tb_tm/write_buffer");
		wait;
	end process signal_spy;


--	
--	Normal mode assertions
-- TODO in own testbench
--
	

	-- TODO why doesn't this fail?
	forwarding: postponed process (reset, clk) is
	begin
		if reset = '1' then
			null;
		elsif rising_edge(clk) then
			assert sc_out_arb = sc_out_cpu;
			assert sc_in_cpu = sc_in_arb;
		end if;
	end process; 

--	
--	Input
--
		
	gen: process is
		variable result: std_logic_vector(31 downto 0);
	
		-- main memory
		
		type ram_type is array (0 to 2**MEM_BITS-1) 
			of std_logic_vector(31 downto 0); 
		alias ram is << signal .memory.main_mem.ram: ram_type >>;

		-- tm state

		alias nesting_cnt is << signal dut.nesting_cnt: nesting_cnt_type >>;
		
		-- write tags
		
		alias write_tags_v is << signal .dut.cmp_tm.write_tags.v: 
			std_logic_vector(2**way_bits-1 downto 0) >>;		
			
		-- read tags
			
		alias read_tags_v is << signal .dut.cmp_tm.read_tags.v: 
			std_logic_vector(2**way_bits-1 downto 0) >>;		
		
		
		--alias buf is << signal .dut.cmp_tm.data: 
		--	data_array(0 to << constant .dut.cmp_tm.lines: integer >> - 1) >>;
		
		type tag_array is array (0 to 2**way_bits-1) of 
			std_logic_vector(addr_width-1 downto 0);
		alias read_tags is << signal .dut.cmp_tm.read_tags.tag: tag_array >>;
		
		-- test data
	
		type addr_array is array (natural range <>) of 
			std_logic_vector(SC_ADDR_SIZE-1 downto 0);
		constant addr: addr_array := 
			("000" & X"049f8", "000" & X"07607", "000" & X"00001"); 
			
		constant data: data_array :=
			(X"aa25359b" , X"acd23bd6", X"42303eea", X"0000007b");
	begin
		-- TODO sc_out_cpu <= sc_out_idle;
	
		wait until falling_edge(reset);		
		wait until rising_edge(clk);
		
		-- normal mode read and write
		
		assert << signal .dut.state: state_type>> = no_transaction;
		
		sc_write(clk, addr(2), data(3), sc_out_cpu, sc_in_cpu);
		assert now = 60 ns;
		assert ram(to_integer(unsigned(addr(2)))) = data(3);
		
		sc_read(clk, addr(2), result, sc_out_cpu, sc_in_cpu);
		assert now = 100 ns and result = data(3);
		
		 

		-- start and end transactions
		
		assert to_integer(unsigned(nesting_cnt)) = 0; 

		sc_write(clk, TM_MAGIC, 
			(31 downto tm_cmd_raw'length => '0') & TM_CMD_START_TRANSACTION, 
			sc_out_cpu, sc_in_cpu);
		
		assert << signal .dut.state: state_type>> = normal_transaction;
		assert to_integer(unsigned(nesting_cnt)) = 1;		

		sc_write(clk, TM_MAGIC, 
			(31 downto tm_cmd_raw'length => '0') & TM_CMD_START_TRANSACTION, 
			sc_out_cpu, sc_in_cpu);

		assert to_integer(unsigned(nesting_cnt)) = 2;
		
		sc_write(clk, TM_MAGIC, 
			(31 downto tm_cmd_raw'length => '0') & TM_CMD_END_TRANSACTION, 
			sc_out_cpu, sc_in_cpu);
		
		assert to_integer(unsigned(nesting_cnt)) = 1;
		
		sc_write(clk, TM_MAGIC, 
			(31 downto tm_cmd_raw'length => '0') & TM_CMD_START_TRANSACTION, 
			sc_out_cpu, sc_in_cpu);
			
		assert to_integer(unsigned(nesting_cnt)) = 2;
		
		assert << signal .dut.conflict: std_logic >> /= '1';
		assert write_tags_v = (2**way_bits-1 downto 0 => '0');
		assert << signal .dut.cmp_tm.write_tags.shift: 
			std_logic >> = '0';
		
		assert read_tags_v = (2**way_bits-1 downto 0 => '0');		
		assert << signal .dut.cmp_tm.read_tags.shift: 
			std_logic >> = '0';
		
		-- writes and reads in transactional mode
		
		sc_write(clk, addr(0), data(0), sc_out_cpu, sc_in_cpu);
		
		assert write_tags_v(0) = '1';
		assert write_tags_v(2**way_bits-1 downto 1) = (2**way_bits-1 downto 1 => '0');  
		-- TODO this does fail, why? assert write_tags_v = (2**way_bits-1 downto 1 => '0', 0 => '1');
		assert write_buffer(0) = data(0);
		assert ram(to_integer(unsigned(addr(0)))) = (31 downto 0 => 'U');
		
		sc_read(clk, addr(0), result, sc_out_cpu, sc_in_cpu);
		
		assert result = data(0);
		
		
		-- read uncached word
		
		sc_read(clk, addr(2), result, sc_out_cpu, sc_in_cpu);
		
		assert result = data(3);		
		assert read_tags(0) = addr(0)(addr_width-1 downto 0);
		assert read_tags(1) = addr(2)(addr_width-1 downto 0);
		
		assert read_tags_v(2**way_bits-1 downto 2) = 
			(2**way_bits-1 downto 2 => '0');
		assert read_tags_v(1 downto 0) = "11";
		

		sc_write(clk, TM_MAGIC, 
			(31 downto tm_cmd_raw'length => '0') & TM_CMD_END_TRANSACTION, 
			sc_out_cpu, sc_in_cpu);
		
		assert to_integer(unsigned(nesting_cnt)) = 1;

 
		sc_write(clk, addr(0), data(1), sc_out_cpu, sc_in_cpu);
		
		assert write_tags_v(0) = '1';
		assert write_tags_v(2**way_bits-1 downto 1) = (2**way_bits-1 downto 1 => '0');  
		assert write_buffer(0) = data(1);
		assert << signal .dut.cmp_tm.newline:
			std_logic_vector(way_bits-1 downto 0) >> = 
			(way_bits-1 downto 2 => '0') & "01";
		
		sc_read(clk, addr(0), result, sc_out_cpu, sc_in_cpu);
		
		assert result = data(1);
						
		sc_write(clk, addr(1), data(2), sc_out_cpu, sc_in_cpu);

		assert write_buffer(0) = data(1);
		assert write_buffer(1) = data(2);
		assert << signal .dut.cmp_tm.newline:
			std_logic_vector(way_bits-1 downto 0) >> = 
			(way_bits-1 downto 2 => '0') & "10";
		
		
		-- commit transaction

		testing_commit <= true;
		
		sc_write(clk, TM_MAGIC, 
			(31 downto tm_cmd_raw'length => '0') & TM_CMD_END_TRANSACTION, 
			sc_out_cpu, sc_in_cpu);
		
		testing_commit <= false;
		
		assert << signal .dut.state: state_type>> = no_transaction;
		assert to_integer(unsigned(nesting_cnt)) = 0;
		
		assert ram(to_integer(unsigned(addr(0)))) = (data(1));
		assert ram(to_integer(unsigned(addr(1)))) = (data(2));		
		
		
		
		-- start another transaction
		
		sc_write(clk, TM_MAGIC, 
			(31 downto tm_cmd_raw'length => '0') & TM_CMD_START_TRANSACTION, 
			sc_out_cpu, sc_in_cpu);
		
		assert << signal .dut.state: state_type>> = normal_transaction;
		
		-- no conflict
				
		broadcast <= (
			valid => '1',
			address => addr(0));
			
		wait until rising_edge(clk);
		assert exc_tm_rollback = '0';
		
		broadcast.valid <= '0';
		
		for i in 2 to conflict_detection_cycles loop
			wait until rising_edge(clk);
			assert exc_tm_rollback = '0';
		end loop;
		
		
		-- conflict
		
		sc_read(clk, addr(0), result, sc_out_cpu, sc_in_cpu);
		
		testing_conflict <= true;
		
		broadcast <= (
			valid => '1',
			address => addr(0));
		
		wait until rising_edge(clk);
		
		broadcast.valid <= '0';
		
		for i in 2 to conflict_detection_cycles+1 loop
			
			assert i <= conflict_detection_cycles;
			exit when exc_tm_rollback = '1';
			wait until rising_edge(clk);					
		end loop;

		testing_conflict <= false;
		
		finished <= true;
		write(output, "Test finished.");
		wait;
	end process gen;
	
	
	check_flags: process is
	begin
		wait until falling_edge(reset);
		loop
			wait until rising_edge(clk);
			
			assert commit_out_try = '0' or testing_commit;
			assert exc_tm_rollback = '0' or testing_conflict;
		end loop; 
	end process check_flags;

	
		--assert commit_out_try /= '1';
	--assert exc_tm_rollback /= '1';
	

--
--	Generic
--

	memory: entity work.mem_no_arbiter(behav)
	generic map (
		MEM_BITS => MEM_BITS
		)
	port map (
		clk => clk,
		reset => reset,
		sc_mem_out => sc_out_arb,
		sc_mem_in => sc_in_arb
		);

	clock: process
	begin
	   	wait for cycle/2; clk <= not clk;
	   	if finished then
	   		wait;
	   	end if;
	end process clock;

	process
	begin
		reset <= '1';
		wait for reset_time;
		reset <= '0';
		wait;
	end process;
	

end;
