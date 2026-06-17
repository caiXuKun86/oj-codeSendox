import sys

def main():
    # 一次性读取并切分流
    input_data = sys.stdin.read().split()
    if not input_data:
        return
    
    n = int(input_data[0])
    total_sum = 0
    
    # 限制边界避免越界
    limit = min(n + 1, len(input_data))
    for i in range(1, limit):
        total_sum += int(input_data[i]) // 2
        
    print(total_sum)

if __name__ == '__main__':
    main()